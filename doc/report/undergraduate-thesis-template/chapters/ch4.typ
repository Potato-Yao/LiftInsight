= 程序架构设计与技术实现方案

== 总体技术方案的确定

截至项目立项时，我已经在诊所工作三个学期了。这段期间，特别是第二学年诊所规范要在换硅脂操作后烤机，我发现很多需要使用工具软件的操作都固定且繁琐，所以如果能有一个软件取代人去自动进行这些操作，诊所的工作效率和质量都会得到提升。恰好这学期 Java 课以项目作为作业，Java 也是我熟悉的语言，我有做出项目的信心。

于是，我归纳了诊所常用的各种操作，提取出程序实现的核心需求：硬件指标的读取、外部工具的调用。我必须先明确这两个需求怎么实现，才能继续开发项目。

关于获取硬件指标，我首先去寻找 AIDA64 和 HWiNFO 的文档，发现它们均为闭源软件，没有可供外部调用的 API。因此，我转而寻找开源的替代项目。我在网上搜索得到项目 OpenHardwareMonitor，不过已经停止维护了。取而代之的是 LibreHardwareMonitor，它是使用 C# 语言编写的，我并不熟悉。但是我在仔细搜索、询问 ChatGPT 后都没有得到其它替代选择，便尝试使用 LibreHardwareMonitor。

LibreHardwareMonitor 分为 UI 和 LibreHardwareMonitorLib 两部分，但是没有提供任何文档。我在 GitHub 找到了对其进行封装的项目，但是同样没有提供文档或者代码示例。最终我找到了一个叫做 fan-control 的 Rust 项目，它包含了一个 LibreHardwareMonitorLib 的 wrapper，可以方便地进行通信、获取硬件数据。于是我将 Rust 代码翻译为 Java 代码，并小规模重构了 wrapper，解决了第一个问题。

关于需要调用的外部工具主要分为三类：第一是使用图形化界面的；第二是使用命令行的；第三是文本化界面或者需要在程序的解释器环境执行命令的。第一种很难实现自动操作，我的解决方案是寻找不需要操作图形化界面的替代品。我使用 Furmark2（支持命令行操作）替代常用的 Furmark，使用命令行操作的 cpu burn 替代 cpuburner。第二类只需使用 Process 类进行调用即可。第三类需要在 Process 进行相对复杂的 IO 操作，但仍是可以实现的。

关于总体架构，我选择使用模块化设计，将内核与交互界面分开，从而便于项目的维护和模块间功能的分别、隔离。

== 数据存储和处理

本项目处理的数据为各硬件指标，指 CPU、GPU、内存和电池的各项参数，和系统信息，指硬盘分区、BitLocker 状态、系统版本等。硬件指标是从 LibreHardwareMonitorLib 获取的，系统信息则来自 diskpart、manage-bde 和 systeminfo 等程序。

要想获取硬件指标，首先需要向 LibreHardwareMonitor 发出请求获取 hardware list。hardware list 是一个 JSON 字符串，储存了 LibreHardwareMonitor 能够获取的所有硬件信息以及对应的索引。向 LibreHardwareMonitor 发送索引就能得到对应的值。因此，本程序会遍历 hardware list 并解析并存储需要的项。当需要更新硬件指标时，程序遍历存储的项，依次向 LibreHardwareMonitor 获取对应的值。见 @lhm_section。

关于系统信息的处理则更为复杂，因为 diskpart、manage-bde 和 systeminfo 等程序并不会按照任一数据储存格式返回结果，而是返回便于人类阅读的文本，需要按照具体形式进行特殊处理。大部分程序返回的结果都有固定的形式，因此只需要直接针对某一行的某位置的子字符串进行读取即可。但是如 manage-bde 返回的结果结构上更多样，为了避免信息提取错漏，我为其设计了状态机。见 @managebde_section。

关于信息的存储。除储存烤机测试信息外，本程序并没有其它向本地储存文件的需求。在烤机时，程序会实时将烤机的用时、CPU 温度、CPU 功率、GPU 温度和 GPU 功率储存在程序同级目录的 CAFiles 文件夹下，使用 CSV 格式存储。

使用 CSV 主要出自存储数据的使用场景考虑：导出烤机数据是为了在需要时复盘烤机结果。这需要一种直观简单的格式，以便于人工进行数据阅读和比对，并最好能够导入到如 Excel 等软件进行可视化分析。这就排除了 JSON、XML 和 TOML 等常用格式。因此，紧凑、表格形式组织的 CSV 就成为了合适的选择。另外，需要导出数据的种类是可被提前确定的，因此使用固定列的格式更能够使得数据紧凑直观，这同样是拒绝 TOML、JSON 和 XML 等的因素。

== 架构设计、数据结构和算法的面向对象实现技术方案 <design_section>

本项目分为 Kernel 和 Desktop 两个模块。

Kernel 提供了程序需要的一切获取硬件信息、与外部工具交互的方法，分为 External、Hardware、Software 和 Utils 四个包。

#figure(
  three-line-table(
    header: ([包名], [功能]),
    [External], [包含与 LibreHardwareMonitorLib wrapper 等外部工具交互的 helper 类],
    [Hardware], [包含用于表示各个硬件的 JavaBean、获取硬件信息的 manager 类],
    [Software], [包含用于表示系统信息的 JavaBean、获取系统信息的 manager 类],
    [Utils], [包含管理员权限检测等杂项工具],
  ),
  caption: [Kernel 模块],
)

Desktop 是程序交互界面的实现，使用 JavaFX 实现，分为 Component、Controller 和 Utils 三个包。

#figure(
  three-line-table(
    header: ([包名], [功能]),
    [Component], [包含对一些 JavaFX 组件的封装],
    [Controller], [包含各个 stage 的 controller],
    [Utils], [包含 CSV 和对话框等杂项工具],
  ),
  caption: [Desktop 模块],
)

本项目在设计上充分采取面向对象设计中的封装思想。为了便于对权限进行控制、避免因修改权限导致下游代码需要修改，本项目中所有成员变量均为 private 标记，使用 public 的 Getter 和 Setter 对外开放。

本项目还使用了若干设计模式，使得代码组织良好、后期改动成本下降。最常用的是 Builder 模式和 Singleton 模式。Builder 模式用于可能加入更多需要赋值、可有默认值的类。如 @stress_test_builder_code 所示。

#figure(
  [
```java
/**
 * builder for {@code StressTestUtil}
 */
public class StressTestUtilBuilder {
    private final StressTestUtil util;
    public StressTestUtilBuilder() throws IOException {
        this.util = new StressTestUtil();
    }
    public StressTestUtilBuilder cpuTest(boolean needTest) {
        util.setTestCPU(needTest);
        return this;
    }
    // 省略若干代码
    public StressTestUtil build() {
        return util;
    }
}
```
  ],
  caption: [StressTestUtilBuilder],
) <stress_test_builder_code>

Singleton 模式用于因为成员变量一定相同、实例没有功能上区别从而本就不应出现多个实例的类，如各个 Manager 类和 Helper 类。如 @singleton_code 所示。

#figure(
  [
```java
private static HardwareInfoManager manager;
/**
 * the constructor will parse hardware list by LHM, map hardware sensors with index in the list.
 * then it will update sensors value by calling {@code update()} periodically.
 *
 * @throws IOException
 */
private HardwareInfoManager() throws IOException {
    // 省略若干代码
}

public static HardwareInfoManager getHardwareInfoManager() throws IOException {
    if (manager == null) {
        manager = new HardwareInfoManager();
    }
    return manager;
}
```
  ],
  caption: [HardwareInfoManager],
) <singleton_code>

对于储存硬件信息的各个 Java Bean 和图形化界面的各个 Controller，都定义了父类，以便减少大量重复代码，并便于进行泛型操作。

借助继承关系，在 Desktop 模块中，我使用泛型编写了生成新的窗口，即其对应的 Controller 和 Stage 的方法，使得创建窗口只需要调用函数即可一行解决，极大减少了创建新窗口业务逻辑的重复代码量。如 @making_window_code 所示。

#figure(
  [
```java
public <Con extends Controller> Pair<Con, Stage> openWindow(String fxml, String title, Class<Con> controllerClass) {
    try {
        // 国际化相关代码
        // FXML 和 Stage 相关代码
        // Controller 相关代码
        // 图标相关代码
        stage.show();
        openedWindows.put(controller, stage);
        return new Pair<>(controller, stage);
    } catch (Exception e) {
        // 省略若干代码
    }
    return null;
}
```
  ],
  caption: [openWindow 方法],
) <making_window_code>

关于异常。除部分可预料到、可以简单处理的异常外，Kernel 遇到异常不会做任何处理，而是直接向上抛出。这是为了将异常处理置于调用端，增强程序的可定制性，同时降低程序业务逻辑的复杂性。由于时间紧迫，我并没有在前端做好报错的处理。目前版本中，当启动时发生错误，程序会弹出弹窗报错，但是在运行时产生的错误将会直接被忽略。