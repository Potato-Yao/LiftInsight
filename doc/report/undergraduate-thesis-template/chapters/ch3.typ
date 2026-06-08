#let image_scale = 50%
#let small_image_scale = 30%
#let big_image_scale = 80%

= 程序需求分析

== 动因描述

寒假时我接触到了举重这项运动。由于没有教练、全靠自学，我必须得录制很多自己动作的视频以供分析问题。这就导致我手机的储存被大量视频占据。而这些视频中，准备动作占了很大篇幅，可能录制的一分半视频有七十秒是在准备，只有二十秒是真的在做动作，因此实际上占据储存空间大部分的都是无用的片段。因此我想，如果能开发一个程序自动剪辑掉水平中的非动作部分，一定能节省出很多空间。

后来，结合训练的实际需求，我又对这个构想做了扩展，希望能做出一个除了训练视频的归纳外，还有运动参数分析、训练计划安排等功能的APP，名字就叫“LiftInsight”。中文名是deepseek起的，叫“举重明析”。

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

“记录”分为“人体”和“训练”。“人体”记录了体重、身高、最大功率和各项动作的最大重量等各项指标。“训练”记录了每次训练的容量、重量、RPE等，还记录了本次训练的视频。

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

每个记录的详情页都提供了视频的预览、编辑和分析功能。本项目的运动分析功能主要是在这部分承载的。

#figure(
  [
    #grid(
      columns: 2,
      // gutter: 1em,
      stack(image("../assets/video0.jpg", width: image_scale)),
      stack(image("../assets/video1.jpg", width: image_scale)),
    )
  ],
  caption: "视频剪辑和分析界面",
)

“动作”记录了数据库中全部的运动动作和相关信息，也为每个动作动作提供了修改和删除功能。

#figure(
  image("../assets/motion.jpg", width: small_image_scale),
  caption: "动作界面",
)

“计划”界面一方面列出了可用计划、每个计划的动作列表，提供对应的新建、删除和编辑等管理功能。

#figure(
  [
    #grid(
      columns: 3,
      // gutter: 1em,
      stack(image("../assets/plan.jpg", width: big_image_scale)),
      stack(image("../assets/plan1.jpg", width: big_image_scale)),
      stack(image("../assets/plan2.jpg", width: big_image_scale)),
    )
  ],
  caption: "计划界面",
)

实时进行的训练也在这里显示。这里会显示下一个进行的训练的重量和次数等信息，可以招出相机录制运动视频。在一组结束后会出现一个弹窗要求你输入本组训练的一些信息，用于记录情况、对本次训练进行评估。

#figure(
  [
    #grid(
      columns: 2,
      // gutter: 1em,
      stack(image("../assets/motion0.jpg", width: image_scale)),
      stack(image("../assets/motion1.jpg", width: image_scale)),
    )
  ],
  caption: "实时训练界面",
)

“设置”界面是APP的各种设置，如主题、语言等，也可以在这里查看项目的相关信息，如版本和编译时间。

#figure(
  image("../assets/settings.jpg", width: small_image_scale),
  caption: "设置界面",
)

== UI 界面与交互设计

我个人一直很喜欢Material Design 3风格，因此本项目的设计风格来源于谷歌原生安卓系统上的各个系统应用，特别是系统时钟程序的设计，是本项目UI界面与交互逻辑设计的主要启发和参考源。

#figure(
  image("../assets/clock.png", width: small_image_scale),
  caption: "Material Design 3风格的系统时钟",
)
