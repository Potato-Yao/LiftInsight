= 程序架构设计与技术实现方案

== 总体技术方案的确定

本项目的需求比较复杂，需要考虑的技术问题有若干点。

=== 持久化数据储存

训练计划、动作、历史记录、正在进行的训练状态以及个性化设置等的持久化储存对于本项目是非常必要的。我选取了Android平台成熟的Room框架进行持久化数据储存，实际上底层是关系型数据库Sqlite。因此我首先设计了若干table，由ChatGPT将其变成更加成熟健壮的table，使用DAO和Entity模型实现对数据增删查改的接口。

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

=== 运动生理学学计算

本项目采用了若干成熟的运动生理学指标来衡量训练强度、估算恢复时长等。为此我阅读了大量运动生理学相关论文，得到了以下的计算公式。

首先是训练强度。单次动作的训练强度一般使用RPE系数（Rating of Perceived Exertion，主观疲劳感觉评分）来衡量。

#set table(stroke: (top: 0.5pt, bottom: 0.5pt, left: none, right: none))
#table(
  columns: (auto, auto, auto),
  align: (left, left, left),

  table.header([*RPE分值*], [*主观描述*], [*对应举重/力量训练的量化解释*]),

  // 数据行
  [10], [最大努力，无法再完成一次], [做完该组后，完全力竭，即使再加1次也绝对无法完成],
  [9], [非常困难], [做完后感觉还能严格完成*1次*，但第2次不可能],
  [8], [困难], [做完后感觉还能再做*2次*],
  [7], [有些困难], [做完后感觉还能再做*3次*],
  [6], [中等偏上], [做完后感觉还能再做*4次*],
  [5], [中等], [做完后感觉还能再做*5次*],
  [4], [轻到中等], [做完后感觉还能再做*6次*],
  [3], [轻松], [做完后感觉还能再做*7次以上*，但仍有明显费力感],
  [2], [很轻松], [几乎不费力，可以做很多次],
  [1], [极轻松], [如热身或技术练习，基本无疲劳感],
  [0], [无任何感受], [静止状态，无运动],
)

== 数据存储和处理

== 架构设计、数据结构和算法的面向对象实现技术方案
