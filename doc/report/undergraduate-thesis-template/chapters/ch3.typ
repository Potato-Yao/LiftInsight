#import math.op

#import "../image_scale.typ": big_image_scale, image_scale, small_image_scale
#let avg = op("avg")
#let med = op("median")
#let score = op("score_of")
#let center_down_arrow = align(center)[
  #sym.arrow.b
]

#show figure: set block(breakable: true)

= App架构设计与技术实现方案

== 总体技术方案

作为一个面向体育相关的项目，本项目技术方案的重点集中在运动生理学在计算机程序的实现上。此外也有一些属于软件开发本身的技术方案。

=== 训练强度评估

本项目采用了若干成熟的运动生理学指标来衡量训练强度。

单次动作的训练强度一般使用RPE系数（Rating of Perceived Exertion，主观疲劳感觉评分）来衡量。


#set table(stroke: (top: 0.5pt, bottom: 0.5pt, left: none, right: none))
#figure(
  [
    #table(
      columns: (auto, auto, auto),
      align: (left, left, left),

      table.header([RPE分值], [主观描述], [补充说明]),

      [10], [最大努力，无法再完成一次], [做完该组后，完全力竭，即使再加1次也绝对无法完成],
      [9], [非常困难], [做完后感觉还能严格完成1次],
      [8], [困难], [做完后感觉还能再做2次],
      [7], [有些困难], [],
      [6], [中等偏上], [],
      [5], [中等], [],
      [4], [轻到中等], [],
      [3], [轻松], [做完后感觉还能再做7次以上，但仍有明显费力感],
      [2], [很轻松], [几乎不费力，可以做很多次],
      [1], [极轻松], [如热身或技术练习，基本无疲劳感],
      [0], [无任何感受], [静止状态，无运动],
    )
  ],
  caption: "RPE系数",
)


一次完整训练的训练强度一般使用sRPE（session RPE）来衡量。

#figure(
  [
    #table(
      columns: (auto, auto, auto),
      align: (left, left, left),
      //   stroke: (x, y) => (
      //     if y == 0 and x == 0 { (top: 0.5pt, left: none, right: none, bottom: 0.5pt) }
      //     else if y == 0 { (bottom: 0.5pt, left: none, right: none) }
      //     else if y == 10 { (bottom: 0.5pt, left: none, right: none) }
      //     else { (left: none, right: none) }
      //   ),
      table.header([sRPE分值], [主观描述], [对应整节训练课的疲劳/负荷说明]),
      [10], [最大努力，极度疲劳], [彻底力竭，无法再做任何一组或多加一次动作；通常只出现在比赛或极限测试后],
      [9], [非常困难], [整节课极其吃力，结束时有明显想停止的感觉；适合赛前模拟或极限周],
      [8], [困难], [很累但能完成计划所有内容，课后需要充分恢复；常用于高强度周期],
      [7], [有些困难], [强度较高但可坚持，结束时有明显疲劳感但不至于崩溃；常见于增力期主课],
      [6], [中等偏上], [有一定挑战性，但能稳定完成计划，课后感觉“练到位了”],
      [5], [中等], [普通训练课，不轻松也不特别累，能较好完成所有内容],
      [4], [轻到中等], [感觉较为轻松，仍有训练效果，但疲劳感较低；适用于恢复日或技术日],
      [3], [轻松], [低强度训练，基本无累积疲劳；例如纯热身、灵活性训练或轻技术课],
      [2], [很轻松], [几乎无负担，可以连续多天进行相同训练],
      [1], [极轻松], [活动量极小，等同于日常活动或极短时间低强度练习],
      [0], [无任何感受], [没有训练，或训练完全无感（通常不会用于实际评分）],
    )
  ],
  caption: "sRPE系数",
)

借助sRPE就可以计算一次训练的训练强度：

$ "work load" = "sRPE" times "Duration time(in minute)" $

在项目中，每个单次训练和每一次完整训练都持久化记录了上述的各类数据。

=== 人体、杠铃与动作建模

为了对动作进行分析，首先需要将视频中的人物转化为数学模型。一个很自然的方法是记录下若干重要部位、关节的坐标。通过这些数据即可计算各个关节的夹角等数据。对于举重来说，比较重要的有躯干角（脊椎-地面夹角）、臀位角（脊椎-股骨夹角）、膝角（股骨-胫骨夹角）和踝角（胫骨-足弓夹角）。

只要记录下各个坐标和时间的函数关系，就对动作做出了建模。但是在实际情况中，由于模型的识别精度和准确度偏差，这样得到的数据是带有噪声的，不便于后续的数据分析。

因此，我引入了Ramer–Douglas–Peucker算法来减少曲线上的数据点，从而避免出现过多特征干扰数据分析算法的正常工作。

#figure(
  [
    ```c
    int DouglasPeucker(Point points[], int start, int end, double epsilon, Point result[]) {
        // Find point with maximum distance
        double dmax = 0;
        int index = start;

        for (int i = start + 1; i < end; i++) {
            double d = PerpendicularDistance(points[i], points[start], points[end]);
            if (d > dmax) {
                index = i;
                dmax = d;
            }
        }

        int resultCount = 0;

        // If max distance exceeds epsilon, recursively simplify
        if (dmax > epsilon) {
            int leftCount = DouglasPeucker(points, start, index, epsilon, result);
            int rightCount = DouglasPeucker(points, index, end, epsilon, result + leftCount);
            resultCount = leftCount + rightCount - 1;  // Remove duplicate point
        } else {
            result[resultCount++] = points[start];
            result[resultCount++] = points[end];
        }

        return resultCount;
    }
    ```
  ],
  caption: "Ramer–Douglas–Peucker算法的C实现",
  supplement: [算法],
)

在编写生产代码前，我首先编写了一个python程序来进行算法和模型效果的模拟，便于快速验证算法、找出问题。比如上述算法的效果如图（背景浅色的是带噪声的原始数据）。

#figure(
  image("../assets/pcode.png", width: big_image_scale),
  caption: "Ramer–Douglas–Peucker算法处理效果",
)

在实际代码中，我采用了Google的Pose Landmark模型#footnote[https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker]来识别人体。Pose Landmark有Lite、Heavy和Full三个模型。经过实际测试，我采用Full模型作为默认分析的模型，它同时保证了足够的精度和处理速度。

Pose Landmark提供了33个可用的节点，每个节点提供了$x$、$y$和$z$坐标（$z$是推算得到的，手机相机本身不具备深度感知能力），以及每个节点的confidence。

#figure(
  [
    ```text
    0 - nose
    ...
    11 - left shoulder
    12 - right shoulder
    13 - left elbow
    14 - right elbow
    15 - left wrist
    16 - right wrist
    ...
    23 - left hip
    24 - right hip
    25 - left knee
    26 - right knee
    27 - left ankle
    28 - right ankle
    29 - left heel
    30 - right heel
    31 - left foot index
    32 - right foot index
    ```
  ],
  caption: "Pose Landmark支持节点列表",
  supplement: [算法],
)



#figure(
  image("../assets/poselandmark.jpg", width: image_scale),
  caption: "Pose Landmark识别实例",
)

关于杠铃会复杂一些。追踪杠铃（实际上是杠铃片，二者同心）主要是为了根据杠铃片和人体在视频里的比例关系，来求得杠铃运行的实际位移和瞬时速度。

由于透视原理，圆在相机角度改变的视频中会变成椭圆。加之有时黑色的杠铃片会和背景融为一体，简单的物体识别无法胜任杠铃片的自动识别。

考虑到pose landmark人体识别的准确度很高，并且杠铃往往固定在手上，我制定了混合策略以识别杠铃片。

首先，找到手的位置。由于杠铃是由手抓着的，可以借助opencv识别手部附近的直线来找到杠铃杆。而杠铃片又固定于杠铃杆，只需要识别与杠铃直线相连的圆或椭圆，就能实现对杠铃片的识别。

当从侧面录制视频时，手会被杠铃片遮挡而使用上述策略。但是考虑到侧面录制时，杠铃片一般位于视频的中间，不会有很严重的透视变形，因此直接套用识别圆或者椭圆的策略即可。

=== 视频分段与截断

在一个训练视频中，准备动作往往占据较长时间（比如抓举往往需要三十秒去调整站位、预设身体张力，而抓举本身只需要十秒左右），极大浪费了储存空间，因此将非动作部分视频删去是必要的。对于一个有多次重复动作的视频，对其自动分段，就可以实现预览视频时快捷地在动作之间跳转，便于快速分析动作。而要想做到分段，也依赖预先对视频前后无关部分的截断。

上述操作的自动化算法都依赖于对关节与时间函数关系的分析。

首先是重复动作的分段。比如说深蹲，深蹲从站立、下蹲到最低点、再次站起的整个过程中，膝角近似从$180 degree$到$45 degree$再回到$180 degree$。理论上这个过程是连续的，所以它就像一个波的正半部分一样。一次进行了多次动作的训练的关节与时间的图像就是多个这样的峰。另外一般每两次动作之间是有一段短暂的间歇的，波之间并不是连续的。

为此，我首先设计了针对单个曲线进行分段的算法（下文中曲线均使用点的数组而非集合表示）。

#figure(
  kind: "algorithm",
  supplement: [算法],
  caption: [曲线分段算法],
  [
    #align(left)[
      输入：曲线$S_0 = [(t, theta)]$，曲线分组宽度阈值$lambda$，合并数据点宽度阈值$epsilon$. 输出：该曲线的分割策略$R = [(t, theta)]$.

      使用Ramer–Douglas–Peucker算法得到简化曲线$S = [(x, y)]$.

      #center_down_arrow

      找到数组

      $
        italic("minima") = [p_i in S | p_(i - 1).y >= p_i.y and p_(i + 1).y >= p_i.y ]\
        italic("maxima") = [p_i in S | p_(i - 1).y <= p_i.y and p_(i + 1).y <= p_i.y ]
      $

      #center_down_arrow

      根据纵坐标将上述两个数组划分成若干个数组$G_i$和$H_i$，每个数组内部元素的纵坐标相近。即满足

      $
        cases(
          forall x in X_i (| x - avg(X_i) | <= lambda),
          union X_i = Y,
          inter X_i = emptyset
        )
      $

      其中$X$为$G$时$Y$为$italic("minima")$，$X$为$H$时$Y$为$italic("maxima")$.

      #center_down_arrow

      在上述数组中，分别找到平均值最大和最小的.即

      $
        cases(
          italic("min_group") = arg limits(min)_i avg(G_i),
          italic("max_group") = arg limits(max)_i avg(H_i)
        )
      $

      #center_down_arrow

      对这两个数组进行合并邻近的点为一个点.

      即对于$forall x_i in X$，$X$为$italic("min_group")$或$italic("max_group")$，令集合$P_i ={x_j in X | |x_i - x_j| <= epsilon}$，若$|P_i| >= 2$，赋值
      $
        X = X - P_i union {med(P_i)}
      $

      #center_down_arrow

      找出两个集合中集合元素数量最多的一个.若元素数量一致，则选择覆盖范围更广的，即最大横坐标与最小横坐标差值最大的一个.设为集合$italic("selected")$.

      #center_down_arrow

      输出$italic("selected")$.
    ]
  ],
)

在实际录制的视频中，可能有些关节被遮挡，导致对应的数据可信度低。在一些动作中，有些关节的运动幅度是极小的，如杠铃划船中的膝关节和髋关节基本不动，无法产生显著用于分段的特征。因此仅选取某一个关节的数据来进行视频分段是不合理的。因此，项目实际上采用了八个关节的数据，分别是左右股骨与脊椎所在直线的夹角（髋角）、左右股骨与胫骨的夹角（膝角）、左右胫骨与足底平面的夹角（踝角）和左右肱骨与尺骨的夹角（臂角），通过算法筛选中三组最优的，并将其合并作为最终的分段依据。

为了选择最优的三组数据，我们需要设计算法量化一组数据的可信度。这基于四个因素。第一是数据点的个数，数据越多偏差就越小；第二是每个分隔之间的时间，一般做组时每次动作花费的时间是相近的，因此时间彼此越相近越好；第三是关节变化的幅度，越大越好，这是为了避免错误使用了在运动过程中保持固定的关节的数据；第四是选取来做分割的各个值之间相差多大，也就是标准差，这是因为同一动作动作进行得到的最大或最小角度应当是近似的，因此这个因素越小越好。将其归一化后加权就得到了最后量化的指标。

#figure(
  kind: "algorithm",
  supplement: [算法],
  caption: [分割可信度评分算法],
  [
    #align(left)[
      输入：曲线的分割点$R = [(t, theta)]$，曲线分组宽度阈值$lambda$. 输出：该分割策略的可信度$italic("score")$.

      计算数据点个数评分

      $
        italic("count_score") = max((|R|)/5, 1)
      $

      #center_down_arrow

      得到间隔时间数组$I = {|p_(i - 1).x - p_i.x|}, i = 1 dots |R|$.

      计算其标准差$sigma_I$.

      计算间隔稳定度评分
      $
        italic("interval_score") = 1/(1 + sigma_I / avg(I) ) = avg(I) / (avg(I) + sigma_I)
      $

      #center_down_arrow

      计算运动幅度评分
      $
        italic("motion_range_score") = max((theta_(95%) - theta_(5%))/(60 degree), 1)
      $

      #center_down_arrow

      计算分割点的标准差$sigma_R$.

      计算极值稳定度评分
      $
        italic("extrema_score") = 1 / (1 + sigma_R / lambda) = lambda / (lambda + sigma_R)
      $

      #center_down_arrow

      加权得到分割可信度评分
      $
        italic("score") = & 0.25 times italic("count_score") +0.35 times italic("interval_score") \
                          & + 0.25 times italic("extrema_score") +0.15 times italic("motion_range_score")
      $

      #center_down_arrow

      输出$italic("score")$.
    ]
  ],
)

基于上述算法就可以选出最优的若干个分段策略，并合并为最终的策略。

#figure(
  kind: "algorithm",
  supplement: [算法],
  caption: [合并分段算法],
  [
    #align(left)[
      输入：曲线的分割点策略集合$S_0 = {[(t, theta)]}$，取最优分割策略的数量$n$，分割点聚类阈值$epsilon$. 输出：曲线合并后的分割策略$R = [(x, y)]$.

      从$S_0$取$italic("score")$前$n$大的分割点策略$S = {[(t, theta)]}$.

      #center_down_arrow

      展开集合$S$并排序，得到分割点数组$C = [c in A in S]$.

      定义$score(x)$表示点$x$所属分割点策略的$italic("score")$.

      #center_down_arrow

      对这个数组进行合并邻近的点为一个点，此点的值应为被合并点的加权平均.对于只出现在一个分割策略的点，则应当视为噪声舍去.

      即对于$forall c_i in C$，令集合$P_i ={c_j in C | |c_i.x - c_j.x| <= epsilon}$，赋值
      $
        C = cases(
          & C - P_i union {1/(|P_i|)sum_(p in P_i) p_i.x dot score(p_i)} & text(", if") |P_i| > 1,
          & C - P_i & text(", if") |P_i| = 1
        )
      $

      输出$C$.
    ]
  ],
)

本算法自带去除前后非动作部分的能力，只要保证前后部分的身体运动幅度极小或极大以至于与动作明显不一致。这套算法仍有改进空间，#ref(<img_period>)所示是其应用于杠铃划船上的效果。

#figure(
  image("../assets/period_angle_plots.png", width: big_image_scale),
  caption: "分段算法实际效果",
) <img_period>

=== 持久化数据储存

本项目借助Room框架，使用Sqlite进行数据的持久化存储，包括运动动作、训练计划、训练记录，以及应用内信息的存储。数据表都是由我设计、ChatGPT优化改进得到的。各个表的结构如下所示。

```sql
-- 1. motion
CREATE TABLE motion (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    name TEXT NOT NULL
);
CREATE UNIQUE INDEX index_motion_name ON motion (name);
-- 2. plan
CREATE TABLE plan (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    name TEXT NOT NULL,
    cycle_period INTEGER NOT NULL,
    current_index INTEGER NOT NULL DEFAULT 0,
    last_applied_at INTEGER NOT NULL DEFAULT 0
);
-- 3. plan_selection
CREATE TABLE plan_selection (
    id INTEGER NOT NULL,
    current_plan_id INTEGER,
    current_day_epoch INTEGER,
    PRIMARY KEY(id),
    FOREIGN KEY(current_plan_id) REFERENCES plan(id) ON DELETE SET NULL
);
CREATE INDEX index_plan_selection_current_plan_id ON plan_selection (current_plan_id);
-- 4. workout_session
CREATE TABLE workout_session (
    id INTEGER NOT NULL,
    is_workout_going INTEGER NOT NULL DEFAULT 0,
    is_paused INTEGER NOT NULL DEFAULT 0,
    started_at INTEGER NOT NULL DEFAULT 0,
    last_resumed_at INTEGER NOT NULL DEFAULT 0,
    elapsed_before_pause_ms INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY(id)
);
-- 5. workout_progress
CREATE TABLE workout_progress (
    id INTEGER NOT NULL,
    plan_id INTEGER NOT NULL,
    plan_day_index INTEGER NOT NULL,
    next_set_index INTEGER NOT NULL DEFAULT 0,
    active_set_index INTEGER,
    total_set_count INTEGER NOT NULL,
    break_ends_at INTEGER NOT NULL DEFAULT 0,
    is_finished INTEGER NOT NULL DEFAULT 0,
    completed_elapsed_time_ms INTEGER NOT NULL DEFAULT 0,
    active_history_id INTEGER,
    workout_intensity INTEGER,
    PRIMARY KEY(id)
);
-- 6. metaplan
CREATE TABLE metaplan (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    plan_id INTEGER NOT NULL,
    motion_id INTEGER NOT NULL,
    day_index INTEGER NOT NULL,
    sets INTEGER NOT NULL,
    reps INTEGER NOT NULL,
    intensity REAL NOT NULL DEFAULT 0.0,
    weight REAL NOT NULL,
    order_index INTEGER NOT NULL,
    FOREIGN KEY(plan_id) REFERENCES plan(id) ON DELETE CASCADE,
    FOREIGN KEY(motion_id) REFERENCES motion(id) ON DELETE RESTRICT
);
CREATE INDEX index_metaplan_plan_id ON metaplan (plan_id);
CREATE INDEX index_metaplan_motion_id ON metaplan (motion_id);
CREATE UNIQUE INDEX index_metaplan_plan_id_day_index_order_index ON metaplan (plan_id, day_index, order_index);
-- 7. history
CREATE TABLE history (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    plan_id INTEGER NOT NULL,
    start_time INTEGER NOT NULL,
    end_time INTEGER NOT NULL,
    intensity INTEGER NOT NULL DEFAULT 0,
    day_index INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY(plan_id) REFERENCES plan(id) ON DELETE RESTRICT
);
CREATE INDEX index_history_plan_id ON history (plan_id);
-- 8. metahistory
CREATE TABLE metahistory (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    date TEXT NOT NULL,
    rep INTEGER NOT NULL,
    rpe INTEGER NOT NULL,
    weight REAL NOT NULL,
    motion_id INTEGER NOT NULL,
    video_name TEXT,
    video_source TEXT NOT NULL DEFAULT 'CAMERA_CAPTURE',
    imported_video_analysis_mode TEXT NOT NULL DEFAULT 'ESTIMATED',
    imported_reference_label TEXT NOT NULL DEFAULT '',
    imported_reference_pixel_distance REAL,
    imported_reference_distance_meters REAL,
    history_id INTEGER,
    FOREIGN KEY(motion_id) REFERENCES motion(id) ON DELETE RESTRICT,
    FOREIGN KEY(history_id) REFERENCES history(id) ON DELETE SET NULL
);
CREATE INDEX index_metahistory_motion_id ON metahistory (motion_id);
CREATE INDEX index_metahistory_history_id ON metahistory (history_id);
-- 9. video_process_state
CREATE TABLE video_process_state (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    video_name TEXT NOT NULL,
    state TEXT NOT NULL,
    progress INTEGER NOT NULL,
    processed_video_name TEXT
);
CREATE UNIQUE INDEX index_video_process_state_video_name ON video_process_state (video_name);
-- 10. metahistory_bin
CREATE TABLE metahistory_bin (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    date TEXT NOT NULL,
    rep INTEGER NOT NULL,
    rpe INTEGER NOT NULL,
    weight REAL NOT NULL,
    motion_id INTEGER NOT NULL,
    motion_name TEXT NOT NULL,
    video_name TEXT,
    video_source TEXT NOT NULL DEFAULT 'CAMERA_CAPTURE',
    imported_video_analysis_mode TEXT NOT NULL DEFAULT 'ESTIMATED',
    imported_reference_label TEXT NOT NULL DEFAULT '',
    imported_reference_pixel_distance REAL,
    imported_reference_distance_meters REAL,
    history_id INTEGER
);
```

本项目还设计了配套的DAO和Entity模型，以实现对数据增删查改的接口。

```text
├── training
│   └── data
│       ├── BodyMetricDao.kt
│       ├── HistoryDao.kt
│       ├── ImportedVideoRecords.kt
│       ├── LiftInsightDatabase.kt
│       ├── MotionDao.kt
│       ├── MotionStore.kt
│       ├── PlanDao.kt
│       ├── PlanStore.kt
│       ├── TrainingEntities.kt
│       └── TrainingRecords.kt
```

=== Multi-Agent Workflow

本项目的代码是全部由AI完成的。项目的前期使用的是简单的单一agent工作模式。但是这就引入了需求描述不清导致最后生成的代码有误、反复测试或调整导致token消耗过多等问题。并且由于生成代码量太大，我难以全部人工审查，因此留下了很多不良或不规范的代码设计。

因此，在项目的中后期，我在opencode中引入了multi-agent workflow以提升工作效率、减少因描述不够详细造成AI的误解、降低token用量，我称之为devloop。

#figure(
  block(width: 100%, inset: 10pt, {
    set text(font: "Inter", size: 9pt)
    set align(center)
    text(size: 12pt, weight: "bold")[Devloop Multi-Agent Workflow]
    v(8pt)
    let node(label, color, body) = box(stroke: 1.2pt + color, radius: 6pt, inset: 6pt, fill: color.lighten(90%))[
      #text(weight: "bold")[#label] #h(4pt) #text(size: 8pt)[#body]
    ]
    node("User", rgb("#2563eb"), "Requirement")
    v(2pt)
    text(size: 11pt)[↓]
    v(2pt)
    node("Devloop", rgb("#0d9488"), "Orchestrator · Routes to agents")
    v(2pt)
    text(size: 11pt)[↓]
    v(2pt)
    node("1 · Planner", rgb("#16a34a"), "Questions · Constraints · Tasks")
    v(1pt)
    text(size: 7pt, style: "italic")[clarify with user if needed]
    v(1pt)
    text(size: 11pt)[↓]
    v(2pt)
    node("2 · Engineer", rgb("#9333ea"), "Implement · Verify · Test")
    v(2pt)
    text(size: 11pt)[↓]
    v(2pt)
    node("3 · Reviewer", rgb("#dc2626"), "Quality · Architecture · Fit")
    v(2pt)
    text(size: 11pt)[↓]
    v(4pt)
    grid(
      columns: (1fr, 1fr),
      column-gutter: 16pt,
      align(center, {
        box(stroke: 1pt + rgb("#ca8a04"), radius: 4pt, inset: 5pt, fill: rgb("#fefce8"))[
          #text(size: 8pt)[✗ Not approved]
        ]
        v(2pt)
        text(size: 7pt)[→ Devloop sends feedback \
          back to Engineer (max 3×)]
      }),
      align(center, {
        box(stroke: 1pt + rgb("#16a34a"), radius: 4pt, inset: 5pt, fill: rgb("#f0fdf4"))[
          #text(size: 8pt)[✓ Approved]
        ]
        v(2pt)
        text(size: 11pt)[↓]
        v(2pt)
        node("4 · Summarizer", rgb("#ea580c"), "Summary · Tradeoffs")
        v(2pt)
        text(size: 11pt)[↓]
        v(2pt)
        box(stroke: 1pt + gray, radius: 4pt, inset: 5pt)[
          #text(size: 8pt)[Final Response → User]
        ]
      }),
    )
  }),
  caption: [Devloop工作原理],
) <fig-workflow>

需要注意的是，除了图中的几个模型，实际上还有一个用于指挥的agent，称之为devloop。

== 程序架构与实现方案

本项目并未采用严格的前后端分离架构，而是根据页面把代码分成多个模块，每个模块内通过MVC架构来分离UI代码和业务逻辑代码。

```text
├── body
├── camera
├── common
├── home
├── MainActivity.kt
├── motion
├── plan
├── record
├── settings
├── training
├── ui
└── video
```

一个典型的例子是计划界面的组织架构。

```text
├── controller
│   ├── PlanControllerEditorModel.kt
│   ├── PlanControllerEditorSupport.kt
│   ├── PlanController.kt
│   ├── PlanControllerStateSupport.kt
│   └── PlanControllerWorkoutSupport.kt
├── data
│   ├── TrainingPlanSeedData.kt
│   └── TrainingPlanStore.kt
├── model
│   ├── PlanState.kt
│   ├── TrainingPlanState.kt
│   └── WorkoutProgressState.kt
├── PlanEditorScreen.kt
├── PlanScreen.kt
├── PlanTabHost.kt
└── route
    └── PlanRoute.kt
```
