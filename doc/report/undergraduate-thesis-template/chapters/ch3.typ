#let image_scale = 50%
#let small_image_scale = 30%
#let big_image_scale = 80%

= 程序需求分析

== 动因描述

寒假时我接触到了举重这项运动。由于没有教练、全靠自学，我必须得录制很多自己动作的视频以供分析问题。这就导致我手机的储存被大量视频占据。而这些视频中，准备动作占了很大篇幅，可能录制的一分半视频有七十秒是在准备，只有二十秒是真的在做动作，因此实际上占据储存空间大部分的都是无用的片段。因此我想，如果能开发一个程序自动剪辑掉水平中的非动作部分，一定能节省出很多空间。

后来，结合训练的实际需求，我又对这个构想做了扩展，希望能做出一个除了训练视频的归纳外，还有运动参数分析、训练计划安排等功能的APP，名字就叫LiftInsight。

== 竞品分析

安卓平台上有运动记录、分析功能是主要有两款软件，VBTgo和WL Analysis。

WL Analysis功能比较简单——导入或录制视频，它会识别杠铃轨迹、计算瞬时速度，除此以外并无其它功能。另外这个软件是闭源的，软件内广告不少。



#figure(
  [
    #grid(
      columns: 2,
      // gutter: 1em,
      stack(image("../assets/WL 2.jpg", width: image_scale)),
      stack(image("../assets/WL annlysis.jpg", width: image_scale)),
    )
  ],
  caption: "WL Analysis",
)

VBTgo也有杠铃轨迹识别和瞬时速度、功率计算功能，它还可以按照录制的日期对视频进行分组。除此以外它还有身体某些指标的测试功能，但是需要收费，我并没有体验过。因此这款软件的不足也在于一方面功能太少，一方面闭源且收费。

#figure(
  [
    #grid(
      columns: 2,
      // gutter: 1em,
      stack(image("../assets/VBT.jpg", width: image_scale)), stack(image("../assets/VBT2.jpg", width: image_scale)),
    )
  ],
  caption: "VBTgo",
)

== 功能描述

本项目是举重及相关运动（如力量举）的动作分析、训练规划等的APP，软件采用Material Design主题，清晰分明地分为“Home”、“Record”、“Motion”、“Plan”和“Settings”五个部分。

“首页”是APP的首页，提供了今日训练计划速览、快速开始今日训练以及身体恢复情况和今日训练强度等信息的报告。

#figure(
  image("../assets/home.jpg", width: small_image_scale),
  caption: "首页界面",
)

“记录”分为“人体”和“训练”。“人体”记录了体重、身高、最大功率和各项动作的最大重量等各项指标。“训练”记录了每次训练的容量、重量、RPE等，还记录了本次训练的视频。这里还提供了视频的预览、编辑和分析功能。

#figure(
  [
    #grid(
      columns: 3,
      // gutter: 1em,
      stack(image("../assets/record.jpg", width: big_image_scale)),
      stack(image("../assets/training0.jpg", width: big_image_scale)),
      stack(image("../assets/training1.jpg", width: big_image_scale)),
    )
  ],
  caption: "记录界面",
)

// 本程序作为维修电脑的实用工具箱，主要面向有电脑维护维修需求的同学。通过本程序可以直观地观察到电脑的各项硬件信息和指标、方便地进行各类维护诊断操作。具体功能清单如下：

// 硬件信息和系统信息监视：实时监视电脑的 CPU 温度、功率、频率；GPU 温度、功率、频率；内存大小与占用率；物理硬盘数量及大小；电池充电状态、剩余电量、电池健康度和充放电功率；系统的名称、版本号、主板模具、位数、激活状态。如 @monitor_view 所示。

// #figure(
//   image("images/monitor_view.png", width: 100%),
//   caption: [监视器页面],
// ) <monitor_view>

// 实用功能菜单：提供了一键激活 Windows、一键重启并进入 BIOS、一键解锁 BitLocker、一键代理重置、一键网卡 Code56 修复和自动烤机测试功能。如 @tools_view 所示。

// #figure(
//   image("images/tools_view.png", width: 100%),
//   caption: [实用工具页面],
// ) <tools_view>

// 外部功能导航：提供了快速打开外部工具的导航页，点击按钮即可打开对应工具。如 @externals_view 所示。

// #figure(
//   image("images/externals_view.png", width: 100%),
//   caption: [外部工具导航页面],
// ) <externals_view>

// 帮助页面：帮助界面附上了网协 wiki 的链接和各咨询群的群号。如 @help_view 所示。

// #figure(
//   image("images/help_view.png", width: 60%),
//   caption: [帮助界面],
// ) <help_view>

// 关于页面：关于界面附上了网协首页与本项目 GitHub 仓库的链接。如 @about_view 所示。

// #figure(
//   image("images/about_view.png", width: 60%),
//   caption: [关于界面],
// ) <about_view>

// 版本页面：关于界面附上了当前 ClinicAssistant 的图形化界面和内核版本。如 @version_view 所示。

// #figure(
//   image("images/version_view.png", width: 40%),
//   caption: [版本界面],
// ) <version_view>

// 一键激活 Windows：使用激活脚本激活 Windows 系统，点击按钮即可激活 Windows 系统，若已激活系统则无法按下激活按钮。如 @activation_tool 所示。

// #figure(
//   image("images/activation_tool.png", width: 20%),
//   caption: [Windows 激活工具],
// ) <activation_tool>

// 一键进入 BIOS：重启电脑并进入 BIOS 设置界面。如 @enter_bios_tool 所示。

// #figure(
//   image("images/enter_bios_tool.png", width: 50%),
//   caption: [重启并进入 BIOS 工具],
// ) <enter_bios_tool>

// 一键解锁 BitLocker：实时识别电脑上所有磁盘分区的 BitLocker 加密情况，在没有完全解密的磁盘上按下左键即可招出确认菜单，确认后自动解锁 BitLocker。如 @bitlock_tools 所示。

// #figure(
//   [
//     #set text(font: songti, size: 10pt)
//     #set stack(dir: ttb, spacing: 0.5em)
//     #grid(
//       columns: 2,
//       gutter: 1em,
//       stack(image("images/bitlocker_tool.png", width: 100%), [(a) BitLocker 状态]),
//       stack(image("images/bitlocker_unlock_tool.png", width: 100%), [(b) BitLocker 解锁]),
//     )
//   ],
//   caption: [BitLocker 解锁工具],
// ) <bitlock_tools>

// 一键重置网络代理：重置网络代理。如 @proxy_tool 所示。

// #figure(
//   image("images/proxy_tool.png", width: 50%),
//   caption: [重置网络代理工具],
// ) <proxy_tool>

// 一键修复网卡代码 56：修复网卡驱动报错“代码 56”。如 @code56_tool 所示。

// #figure(
//   image("images/code56_tool.png", width: 50%),
//   caption: [重置网络代理工具],
// ) <code56_tool>

// 自动烤机：勾选需要烤 CPU 或和 GPU，输入被烤电脑电源适配器的功率（可选），自动进行烤鸡测试。在测试时可以查看已测试时间、“温度-时间图象”和“功率-时间图象”、算法对指标的实时评估。当算法判定烤机效果已经可以说明散热良好时烤机自动结束。若 CPU 或 GPU 温度超出安全上限则烤机自动结束。如 @stress_test_tool 所示。

// #figure(
//   image("images/stress_test_tool.png", width: 100%),
//   caption: [自动烤机测试工具],
// ) <stress_test_tool>

// 烤机时会实时将当前电脑的硬件指标储存在位于 CAFiles 文件夹下的 CSV 文件中，以供参考和手动判断烤机效果。

== UI 界面与交互设计

// 本项目从诊所工作的实际情况出发进行设计，目标在于使得常用参数指标易于观察、常用操作可被“一键”执行，从而方便诊所的日常工作。因此，我将软件设计成了“监视器”“工具”和“外部工具”三部分，使用左侧栏进行切换。如 @monitor_view、@tools_view 和 @externals_view 所示。

// 为了这个目标，整个软件都遵循着简单清晰的设计原则：仅使用按钮、文本和图表来设计交互、展示信息，以牺牲部分可定制性和更加详细的信息，换来更快速易于上手的交互逻辑。如 BitLocker 解锁工具（见 @bitlock_tools）和自动烤机工具（见 @stress_test_tool）就是这种设计的典型例子：BitLocker 解锁工具只设计了解锁而无加锁、自动烤机工具只设计了自动判断而无定时功能，就是因为这些需求对诊所工作而言相当罕见，因此无需进行额外设计。

// 本项目支持国际化，图形化界面的默认语言是英语。这是为了避免系统编码配置有误导致的界面乱码，这常见于使用英文作为显示语言或开启了全局 UTF-8 编码实验功能的系统。
