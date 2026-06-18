#import "template.typ": *

#show: paper.with(
  //anonymous: true, // 需要匿名盲审可以打开此选项(去除封面、原创性声明页)
  //twoside: true, // 打印纸质版时可以打开此选项(在封面和声明页处加入空白页)
  subject: "结课作业报告",
  title: "《Android技术开发基础结课作业报告》",
  title-en: "The Report of Foundation of Android Programming",
  college: "计算机学院",
  major: "数据科学与大数据技术（全英文教学专业）",
  // class: "计科2301班",
  author: "杨紫诺",
  student-id: "1120234514",
  guide-teacher: "暂无，求包养",
  date: datetime.today(),
  //date: datetime(year: 2025, month: 8, day: 31),
  declare: true, // true:svg形式的声明页,false:typst生成的声明页，none:去除声明页

  // 若 abstract-content 为空，或参数缺省，则不显示中文摘要
)

#import "image_scale.typ": big_image_scale, image_scale, small_image_scale

#include "chapters/ch1.typ"
#include "chapters/ch2.typ"
#include "chapters/ch3.typ"
#include "chapters/ch4.typ"

== 参考文献引用

引用时直接根据 `bib` 文件中的 `key` 作为参数引用即可。

目前本模板采用的是 `Typst` 内置的 `gb-7714-2015-numeric` 格式。与学校的要求有一定出入。

// 内置引用函数
#cite(<yuFeiJiZongTiDuoXueKeSheJiYouHuaDeXianZhuangYuFaZhanFangXiang2008>)

#cite(<OBRIEN1994Aircraft>)aaa#cite(<张伯伟2002全唐五代诗格会考>)

#cite(<OBRIEN1994Aircraft>)#cite(<张伯伟2002全唐五代诗格会考>)

#cite(<OBRIEN1994Aircraft>)
#cite(<张伯伟2002全唐五代诗格会考>)

#cite(<Hajela2012Application>)
#cite(<Sobieski>)

// 模板自定义引用函数，可以引用多个文献
#bib-cite(<fengxiqiao>, <Sobieszczanski>, <jiangxizhou>, <xiexide>, <yaoboyuan>)

#bib-cite(<yuFeiJiZongTiDuoXueKeSheJiYouHuaDeXianZhuangYuFaZhanFangXiang2008>, <Hajela2012Application>)

#bib-cite(
  <yuFeiJiZongTiDuoXueKeSheJiYouHuaDeXianZhuangYuFaZhanFangXiang2008>,
  <Hajela2012Application>,
  <张伯伟2002全唐五代诗格会考>,
  <OBRIEN1994Aircraft>,
  <雷光春2012>,
  <白书农>,
  <zhanghesheng>,
  <Sobieski>,
  <fengxiqiao>,
  <Sobieszczanski>,
  <jiangxizhou>,
  <xiexide>,
  <yaoboyuan>,
)

#references("./ref.bib")

#appendices()[

  附录相关内容…

  附录是毕业设计（论文）主体的补充项目，为了体现整篇文章的完整性，写入正文又可能有损于论文的条理性、逻辑性和精炼性，这些材料可以写入附录段，但对于每一篇文章并不是必须的。附录依次用大写正体英文字母A、B、C……编序号，如附录A、附录B。【阅后删除此段】

  附录正文样式与文章正文相同：宋体、小四；行距：22磅；间距段前段后均为0行。【阅后删除此段】

]
