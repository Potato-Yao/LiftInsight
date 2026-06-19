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
#include "chapters/ch5.typ"
#include "chapters/ch6.typ"

// == 参考文献引用

// 引用时直接根据 `bib` 文件中的 `key` 作为参数引用即可。

// 目前本模板采用的是 `Typst` 内置的 `gb-7714-2015-numeric` 格式。与学校的要求有一定出入。

// // 内置引用函数
// #cite(<yuFeiJiZongTiDuoXueKeSheJiYouHuaDeXianZhuangYuFaZhanFangXiang2008>)

// #cite(<OBRIEN1994Aircraft>)aaa#cite(<张伯伟2002全唐五代诗格会考>)

// #cite(<OBRIEN1994Aircraft>)#cite(<张伯伟2002全唐五代诗格会考>)

// #cite(<OBRIEN1994Aircraft>)
// #cite(<张伯伟2002全唐五代诗格会考>)

// #cite(<Hajela2012Application>)
// #cite(<Sobieski>)

// // 模板自定义引用函数，可以引用多个文献
// #bib-cite(<fengxiqiao>, <Sobieszczanski>, <jiangxizhou>, <xiexide>, <yaoboyuan>)

// #bib-cite(<yuFeiJiZongTiDuoXueKeSheJiYouHuaDeXianZhuangYuFaZhanFangXiang2008>, <Hajela2012Application>)

// #bib-cite(
//   <yuFeiJiZongTiDuoXueKeSheJiYouHuaDeXianZhuangYuFaZhanFangXiang2008>,
//   <Hajela2012Application>,
//   <张伯伟2002全唐五代诗格会考>,
//   <OBRIEN1994Aircraft>,
//   <雷光春2012>,
//   <白书农>,
//   <zhanghesheng>,
//   <Sobieski>,
//   <fengxiqiao>,
//   <Sobieszczanski>,
//   <jiangxizhou>,
//   <xiexide>,
//   <yaoboyuan>,
// )

// #references("./ref.bib")
