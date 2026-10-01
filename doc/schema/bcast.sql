-- bcast 广播电视安全播出与播出事故处置管理 -- schema (chen-021)
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：chen_021

CREATE TABLE IF NOT EXISTS t_bcast_case_flow (
  id bigint NOT NULL COMMENT '主键',
  biz_no varchar(64) DEFAULT NULL COMMENT '播出事故处置单代号',
  stage int DEFAULT NULL COMMENT '当前步次 0..3（发现核实/处置抢险/恢复确认/上报结案）',
  status int DEFAULT NULL COMMENT '处置会落 0未起 1在办 2已收了口',
  content varchar(255) DEFAULT NULL COMMENT '一段一记',
  last_action varchar(64) DEFAULT NULL COMMENT '最近一次挪步动作',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='播出事故处置单';

CREATE TABLE IF NOT EXISTS t_bcast_off_row (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '运行情况报送册批号',
  row_no int DEFAULT NULL COMMENT '原报行次',
  item_code varchar(64) DEFAULT NULL COMMENT '被对上的监测台代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本行停播分钟数',
  hours_num decimal(12,2) DEFAULT NULL COMMENT '本季满播小时数',
  status int DEFAULT NULL COMMENT '行落地情形 0未销 1已收下 2已退回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运行情况报送核收行';

CREATE TABLE IF NOT EXISTS t_bcast_pre_line (
  id bigint NOT NULL COMMENT '主键',
  rule_code varchar(64) DEFAULT NULL COMMENT '定档门槛线代号',
  rule_name varchar(128) DEFAULT NULL COMMENT '线名',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '起算数值(分钟)',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '过半数值(分钟)',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '封顶数值(分钟)',
  eff_start datetime DEFAULT NULL COMMENT '立线之日',
  eff_end datetime DEFAULT NULL COMMENT '让位之日(不含)',
  priority int DEFAULT NULL COMMENT '让位顺位(数值大的先说话)',
  status int DEFAULT NULL COMMENT '线的情形 0在场 1已停用',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='事故定档门槛线';

CREATE TABLE IF NOT EXISTS t_bcast_site (
  id bigint NOT NULL COMMENT '主键',
  site_no varchar(64) DEFAULT NULL COMMENT '发射台站代号',
  site_name varchar(128) DEFAULT NULL COMMENT '台站全称',
  site_type varchar(32) DEFAULT NULL COMMENT '台站类别',
  road_name varchar(128) DEFAULT NULL COMMENT '属地那一路(省—市—县)',
  status int DEFAULT NULL COMMENT '底册情形 0在册 1已关停',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发射台站底册';

CREATE TABLE IF NOT EXISTS t_bcast_stop_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '停播检修核准单',
  node_no int DEFAULT NULL COMMENT '当前所在档口 0..2（台站/市广电局/省局）',
  sign_mode int DEFAULT NULL COMMENT '同档点齐办法 0一笔即可 1两笔点齐',
  need_count int DEFAULT NULL COMMENT '本档应点名数',
  sign_count int DEFAULT NULL COMMENT '本档已落名数',
  status int DEFAULT NULL COMMENT '核准情形 0在核 1已排定 2已挪回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='停播检修核准单';

CREATE TABLE IF NOT EXISTS t_bcast_unit_card (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '台站核点卡卡号',
  node_no int DEFAULT NULL COMMENT '属地层级序',
  site_id int DEFAULT NULL COMMENT '所属发射台站',
  site_no varchar(64) DEFAULT NULL COMMENT '所属台站代号',
  should_count int DEFAULT NULL COMMENT '应核点位数',
  done_count int DEFAULT NULL COMMENT '已核点位数',
  lack_count int DEFAULT NULL COMMENT '还欠点位数',
  content varchar(255) DEFAULT NULL COMMENT '随卡交来的属地要件',
  status int DEFAULT NULL COMMENT '立卡进展 0待核 1已核讫 2压卡',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='台站核点卡立卡单';

CREATE TABLE IF NOT EXISTS t_bcast_zero_task (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '零报告催办条目代号',
  due_at datetime DEFAULT NULL COMMENT '该出手那一日的止点时刻',
  amount decimal(12,2) DEFAULT NULL COMMENT '最可提前几日开口',
  content varchar(255) DEFAULT NULL COMMENT '事由与所对台站的记要',
  status int DEFAULT NULL COMMENT '条目情形 0候催 1已催到 2催不动',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='零报告催办到期单';

-- 台站底册初始数据：id=0 在册启用（status=0）、id=1 已关停（status=1）
INSERT IGNORE INTO t_bcast_site (id, site_no, site_name, site_type, road_name, status, del_flag, create_by, create_time)
VALUES (0, 'TZ00', '苗岭转播台（无线发射，在册）', '无线发射台', '长岭省—河湾区—苗岭县', 0, 0, 'seed', NOW()),
       (1, 'TZ01', '滨河北微波站（线路迁改后关停）', '微波站', '长岭省—河湾区—滨河北', 1, 0, 'seed', NOW());

