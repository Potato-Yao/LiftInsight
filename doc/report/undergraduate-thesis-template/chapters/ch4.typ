= 技术亮点、关键点及其解决方案

== 技术亮点

本项目借助简洁大方的UI界面，实现了训练项目规划和实时跟踪、训练动作分析和回顾等功能。

为了方便视频的回顾和分析，本项目实现了一个简易的视频剪辑工具和视频播放器。

== 技术难点

本项目最难实现的点有三个，算法性质的是人物和物体的识别、视频的分段；工程性质的是上文所说的视频剪辑工具和视频播放器的实现。

=== 人物和物体的识别

在项目开始前，我就尝试过使用pose landmark进行人物动作的识别。由于此模型效果极佳，人物动作识别方面我并没有做更多微调。

=== 视频剪辑工具

=== 视频播放器

=== Agent模型的选择

本项目的Multi-agent workflow中选用的模型经历了若干次变化。

在最开始，我可用的模型是来自GitHub copilot的GPT 5.4和deepseek v4，于是选择了GPT作为devloop、planner和engineer，deepseek pro作为reviewer和summarizer。

后来GPT的额度耗尽，同时我发现reviewer和summarizer的工作较为简单，无需高级模型即可胜任。于是为了降低成本、加快速度，改成了deepseek pro作为devloop、planner和engineer，deepseek flash作为reviewer和summarizer。

但是deepseek速度实在太慢，并且它作为planner对模糊需求的猜测和计划制定的能力并不足。因此我又购入了Xiaomi mimo 2.5，使用mimo 2.5 pro作为devloop和planner，deepseek pro作为engineer，deepseek flash作为reviewer和summarizer。这样效率和工作正确度就高了很多。之所以不用更聪明的mimo作为engineer，是因为engineer是token用量最大的地方，而当planner的计划描述足够清晰时，不同模型在代码质量上的差异很小，因此选用了deepseek进行这项工作。

之后mimo额度耗尽，改用中转站的GPT 5.5代替mimo。

== 项目缺陷
