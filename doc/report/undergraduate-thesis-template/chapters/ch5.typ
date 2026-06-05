= 技术亮点、关键点及其解决方案

== 技术亮点

本项目提供的各种一键式、自动化操作，极大地方便了业务强度高或技术不熟练的电脑维修者。通过拟合函数的方式判断烤机结果是重要的将经验建模为数学公式的尝试，为更多可能的自动化操作给出了基本思路和方法参考。

== 技术关键点

本项目采用多种设计模式，使得代码易于修改，避免了因使用不当造成的错误。见 @design_section。

本项目的图形化界面使用 JavaFX 编写，使用了 MVC 模式组织。

本项目采用了多线程编程。各个软硬件信息获取工具在实例被创建后均会启动自己的 updater 线程从而自动更新数据，避免调用端因忽视数据更新造成错误。

本项目采用 Gradle 作为构建工具，部分核心代码使用了单元测试，代码使用 git 进行版本管理并同步于 GitHub。

本项目使用了三种编程语言，使用 TCP 协议进行通信，使得开发过程更加高效、与非 Java 程序交流便捷。见 @develop_platform_chapter。

本项目核心代码均为手写，提升了个人程序开发和资料收集能力，使得代码具有个人风格。见 @ai_usage_chapter。

== 技术难点

本项目开发过程中的难点主要集中在编写 Kernel 时与 Windows“斗智斗勇”上。

=== 操作 diskpart

要想获取硬盘信息，必须要进入 diskpart 软件的解释器环境，才能输入命令、进行操作。因此，我必须编程模拟人工输入命令、等待解释器执行并返回结果的过程。最开始我使用运行 diskpart 的 process 的 outputStream 模拟输入指令，调用 inputStream 的 readLine 方法来读取返回的结果。但是每次返回的都是 diskpart 开启时打印的版本和版权等信息，获得不到命令的执行结果。

经过多次手动模拟，我发现 diskpart 本身执行较慢。因此如果一经启动 diskpart 就输入指令，由于 diskpart 的解释器环境尚未开启，输入的指令就不会执行，自然无法得到正确的结果。只需要在启动 diskpart 和输入指令这两个过程后等待 500 毫秒再去获取输出，就可以得到正常结果了。

另外，在启动 diskpart 后，必须要消耗掉最初的信息。在读取执行结果时也要删去表示可以执行下一次指令的 "\\n DISKPART>" 字样。代码如 @diskparts0_code 和 @diskparts1_code 所示。

#figure(
  [
    ```java
    /**
     * connect to the necessary tools for disk management
     *
     * @throws IOException
     */
    public void connect() throws IOException {
        // 省略若干行
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            // 省略若干行
        }
        // consume initial output
        while (dpReader.ready()) {
            dpReader.read();
        }
        // wait for diskpart processing
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            // 省略若干行
        }
        while (dpReader.ready()) {
            dpReader.read();
        }
        // 省略若干行
    }
    ```
  ],
  caption: [删除 diskpart 启动时的信息],
) <diskparts0_code>

#figure(
  [
    ```java
    /**
     * all diskpart commands can be executed under its interpreter, they can't be called externally
     * <p>
     * thus, this method is designed for executing command in the interpreter
     *
     * @param command
     * @return
     * @throws IOException the interpreter is not loaded(check if {@code connect()} has been called), or the command just executed failed
     */
    private String executeDPCommand(String command) throws IOException {
        // 省略若干行
        dpWriter.write(command);
        dpWriter.newLine();
        dpWriter.flush();
        // wait for diskpart to process the command
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            // 省略若干行
        }
        StringBuilder output = new StringBuilder();
        while (dpReader.ready()) {
            output.append((char) dpReader.read());
        }
        // remove "\\n DISKPART>"
        output.delete(output.length() - 11, output.length());
        // 省略若干行
    }
    ```
  ],
  caption: [执行 diskpart 指令],
) <diskparts1_code>

=== 解析 manage-bde 的返回信息 <managebde_section>

要想获取磁盘分区的信息，就需要使用 manage-bde --status 指令。但是 manage-bde 返回的消息根据硬盘盘符、BitLocker 状态、分区大小、文件系统等会出现不固定的格式，如果使用常规的逐行分析赋值的方法可能会导致错失信息或更严重的信息赋值错位。我设计使用状态机来处理 manage-bde 返回的数据。这样，一旦有任何信息的缺失，在状态机执行完毕时都可以被检测到。状态机实现如 @state_machine_code 所示。

#figure(
  [
    ```java
    // to be aware of missing info, we decide to use state machine
    int state = 0;  // 0: looking for volume, 1: looking for size, 2: looking for percentage
    while ((line = reader.readLine()) != null) {
        String[] lines = line.trim().split("\\s+");
        if (state == 0) {
            if (lines[0].equals("Volume")) {
                currentPartition = lines[1].substring(0, 1);
                state = 1;
            }
        } else if (state == 1) {
            if (lines[0].equals("Size:")) {
                currentSize = Integer.parseInt(lines[1]);
                state = 2;
            }
        } else if (state == 2) {
            if (lines[0].equals("Percentage")) {
                currentPercentage = Double.parseDouble(lines[2].substring(0, lines[2].length() - 1));
                state = 0;
            }
        }

        if (currentPartition != null && currentSize != Config.INT_DEFAULT && currentPercentage != Config.INT_DEFAULT) {
            partitionItems.add(new PartitionItem(currentSize, currentPartition, currentPercentage));

            currentPartition = null;
            currentSize = Config.INT_DEFAULT;
            currentPercentage = Config.INT_DEFAULT;
        }
    }
    if (currentPartition != null || currentSize != Config.INT_DEFAULT || currentPercentage != Config.INT_DEFAULT) {
    }
    ```
  ],
  caption: [处理 manage-bde 返回信息的状态机],
) <state_machine_code>

=== 从 LibreHardwareMonitorLib 获取信息 <lhm_section>

要想从 LibreHardwareMonitorLib 获取硬件指标，首先需要与其通信获取 hardware list，其具体形式为一个 JSON 字符串，储存 LibreHardwareMonitorLib 能获取到的各个参数，如 @hardware_list_code 所示。

#figure(
  [
    ```json
    [
      {
        "Id": "Load0",
        "Name": "CPU Core #1",
        "Index": 0,
        "Type": "Sensor",
        "Info": "CPU Core #1"
      },
      {
        "Id": "Load1",
        "Name": "CPU Core #2",
        "Index": 1,
        "Type": "Sensor",
        "Info": "CPU Core #2"
      },
      // 省略若干行
      {
        "Id": "Temperature0",
        "Name": "Core Max",
        "Index": 24,
        "Type": "Sensor",
        "Info": "Core Max"
      },
      {
        "Id": "Temperature1",
        "Name": "Core Average",
        "Index": 25,
        "Type": "Sensor",
        "Info": "Core Average"
      },
      {
        "Id": "Temperature2",
        "Name": "CPU Core #1",
        "Index": 26,
        "Type": "Sensor",
        "Info": "CPU Core #1"
      }
      // 省略若干行
    ]
    ```
  ],
  caption: [LibreHardwareMonitorLib 返回的 hardware list],
) <hardware_list_code>

每一项都有 index，向 LibreHardwareMonitorLib 发送索引即可获取对应项的值。

为了储存本程序需要获取的信息在 hardware list 中对应的索引，我定义了 JavaBean Sensor 用来表示 hardware list 中的每一项，如 @sensor_code 所示。

#figure(
  [
    ```java
    package com.potato.kernel.Hardware;

    import com.google.gson.annotations.SerializedName;

    /**
     * fits the result from lhm, represent a sensor info it gives
     */
    public class Sensor {
        @SerializedName("Id")
        private String id;
        @SerializedName("Name")
        private String name;
        @SerializedName("Index")
        private int index;
        @SerializedName("Info")
        private String info;

        // Getters
    }
    ```
  ],
  caption: [JavaBean Sensor],
) <sensor_code>

随后需要遍历所有项，分析其是否需要被存储、应当归为哪类。我定义了数组 int[] index，规定了其中每项用于储存的参数，如 @index_code 所示。

#figure(
  [
    ```java
    /*
    // [0, 32] for cpu
    0 -> cpu load total
    1 -> cpu package temperature
    2 -> cpu core average temperature
    3 -> cpu package power
    4 -> cpu core voltage
    5 -> cpu clock begin
    6 -> cpu clock end

    // 省略若干行
     */
    // stores the sensor index in LHM hardware list
    private int[] index = new int[INDEX_ARRAY_SIZE];
    ```
  ],
  caption: [数组 index],
) <index_code>

在得到所有需要获取的项后，每次更新数据时需要对其遍历、依次询问 LibreHardwareMonitorLib 获取对应值。

为了使设计简单、运行高效，对每一个 Sensor 的分析和更新数据时获取值的方法是如 @ifs0_code 和 @ifs1_code 的若干简单条件判断。

#figure(
  [
    ```java
    if (name.equals("CPU Total") && info.equals("Load")) {
        index[0] = ind;
    } else if (name.equals("CPU Package") && info.equals("Temperature")) {
        index[1] = ind;
    } else if (name.equals("Core Average") && info.equals("Temperature")) {
        index[2] = ind;
        // 省略若干行
    }
    ```
  ],
  caption: [分析 Sensor 数组],
) <ifs0_code>

#figure(
  [
    ```java
    if (index[0] != -1) {
        cpu.setLoad(lhmHelper.getValue(index[0]));
    }
    if (index[1] != -1) {
        cpu.setPackageTemperature(lhmHelper.getValue(index[1]));
    }
    if (index[2] != -1) {
        cpu.setAverageTemperature(lhmHelper.getValue(index[2]));
    }
    // 省略若干行
    ```
  ],
  caption: [获取新值],
) <ifs1_code>

@ifs0_code 和 @ifs1_code 的代码简单、重复，手动编写效率低下且容易出错。因此我编写了 Python 脚本用于生成样板代码，如 @sensor_map_code 所示。

#figure(
  [
    ```python
    import sys

    sensors = [
        (0, "CPU Total", "equals", "Load", "cpu", "setLoad"),
        (1, "CPU Package", "equals", "Temperature", "cpu", "setPackageTemperature"),
        # 省略若干行
    ]

    def warning_message(pos):
        return f"// THE CODE {pos} IS SCRIPT GENERATED, DON'T CHANGE THEM DIRECTLY! CHANGE THE SCRIPT {sys.argv[0]} INSTEAD"

    if __name__ == "__main__":
        below_warning = warning_message("BELOW")
        above_warning = warning_message("ABOVE")

        print(below_warning)
        for i in range(0, len(sensors)):
            if i == 0:
                print(
                    f'if (name.{sensors[i][2]}("{sensors[i][1]}") && info.equals("{sensors[i][3]}")) {{'
                )
            else:
                print(
                    f'}} else if (name.{sensors[i][2]}("{sensors[i][1]}") && info.equals("{sensors[i][3]}")) {{'
                )

            if len(sensors[i]) > 6:
                assert len(sensors[i]) > 6
                print(f"    index[{sensors[i][0]}] = {sensors[i][6]};")
            else:
                print(f"    index[{sensors[i][0]}] = ind;")

        print("}")
        print(above_warning)

        print("\t")

        print(below_warning)
        for i in range(0, len(sensors)):
            print(f"if (index[{sensors[i][0]}] != -1) {{")
            print(
                f"    {sensors[i][4]}.{sensors[i][5]}(lhmHelper.getValue(index[{sensors[i][0]}]));"
            )
            print("}")

        print(above_warning)
    ```
  ],
  caption: [生成样板代码的脚本],
) <sensor_map_code>

=== 子进程管理

在开发中期进行单元测试时，我发现测试结束后程序并没有自行退出。按照设计，程序在开启时会启动 LibreHardwareMonitor，在结束时会向其发送请求要求其关闭。但是借助 Process Explorer，我发现 LibreHardwareMonitor 并没有自行结束。由于子进程没有全部结束，程序本身不会退出。

考虑到强行关闭 LibreHardwareMonitor 并不会造成任何不良影响，我编写了用于强制关闭进程的方法。借助其关闭 LibreHardwareMonitor 即可解决问题。如 @kill_process_code 所示。

#figure(
  [
    ```java
    /**
     * kill a process forcibly
     * @param process
     */
    public static void forceKillProcess(Process process) {
        long pid = process.pid();
        ProcessBuilder killerBuilder = new ProcessBuilder("kill", Long.toString(pid));
        try {
            killerBuilder.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        process.destroy();
    }
    ```
  ],
  caption: [强制关闭进程],
) <kill_process_code>

=== 频率显示方法

CPU 频率是判断 CPU 工作状况的重要指标。CPU 有多个核心，每个核心都有其自己的频率，Windows 任务管理器将其通过某种计算合为了一个频率，下面简称为参考频率，记作 $r_0$，诊所工作经常需要借助任务管理器给出的参考频率进行判断。因此，我需要找出同一将多个核心频率合为一个的办法，得到与任务管理器接近的数值，记作 $r$。

首先，简单观察可知参考频率并不是核心频率中的最大值，也不是其简单平均。我取了最大、第二和第三大的三个频率 $a$、$b$ 和 $c$，使用加权平均公式 @eqt:r0_equation 得到了近似但偏大的拟合结果。

$ r = 0.5 a + 0.25 b + 0.25 c $ <r0_equation>

于是，定义函数 $f(x)$ 使得 $f(r) = r_0$，$r$ 来源于式 @eqt:r0_equation。对电脑手动限制频率，取得数据点集

$ {(0.99, 1), (1.61, 1.24), (2.13, 2.33), (2.55, 2.36), (2.60, 2.44), (2.70, 2.45), (3.10, 2.55)} $

进行牛顿插值，得到多项式 @eqt:r1_equation。

$ r_0' = 30.79 r^6 - 405.53 r^5 + 2179.90 r^4 - 6102.36 r^3 + 9345.26 r^2 - 7382.76 r + 2334.43 $ <r1_equation>

但是式子 @eqt:r1_equation 的拟合效果反而更差。推测是因为任务管理器和我的程序获取的数据可能并不来自同一时刻，因此数据本身是有误的。最后仍是借助尝试加权平均得到了一个近似的方法：记 $a$、$b$、$c$ 和 $d$ 是前四大的频率，单位 MHz，使用加权平均公式 @eqt:r2_equation。

$
  r = cases(
    #[$0.3 a + 0.4 b + 0.2 c + 0.1 d, quad a - b > 500$],
    #[$0.35 a + 0.35 b + 0.2 c + 0.1 d, quad "otherwise"$],
  )
$ <r2_equation>

感叹学艺不精，自己的书本知识和现实实验有很大的分隔。

== 项目缺陷

由于个人缺乏足够的开发经验、在项目开始前没有进行充分的设计，导致项目实际上有若干设计缺陷，增添项目的维护成本。

第一是 Kernel 中各个用于获取信息的 manager 或 helper 没有统一接口，其 updater 线程没有进行统一管理。前者导致在外部调用时需要查阅方法的具体方法名和参数，增大了使用成本。而后者导致当 manager 或 helper 相互调用时，由于 updater 不同步，会造成获取的信息产生最大 1.9 倍获取信息周期的延迟。

第二是 Desktop 中所有窗体实际都是声明在 MainApp 中的。因此在添加新的窗体时不仅要编写 controller，还需要在 MainApp 中添加代码。这添加了太大的耦合性，不利于代码的维护和阅读。正确的设计应该是进行一层封装，使用注册的方式添加新的窗体。

第三是开发时单元测试过少。在开发 Kernel 时为了加快进度，我只写了少量的测试，没有覆盖所有代码、所有情况，导致很多 bug 是在编写 Desktop 甚至是开发完成后出现程序行为有误才发现的。

第四是没有编写日志相关代码，在离开 IDE 后难以获取程序运行的信息和异常。这导致在项目后期将其打包测试时极其难以进行 debug。
