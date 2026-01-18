-- RewardFlow core tables
-- Compatible with MySQL 8.x

CREATE TABLE IF NOT EXISTS play_duration_report (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id VARCHAR(64) NOT NULL,
  sound_id VARCHAR(64) NOT NULL,
  biz_scene VARCHAR(32) NOT NULL,
  duration INT NOT NULL,
  sync_time BIGINT NOT NULL,
  biz_date DATE NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_sound_synctime (user_id, sound_id, sync_time),
  KEY idx_user_scene_date_synctime (user_id, biz_scene, biz_date, sync_time),
  KEY idx_biz_date (biz_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_play_daily (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id VARCHAR(64) NOT NULL,
  biz_scene VARCHAR(32) NOT NULL,
  biz_date DATE NOT NULL,
  total_duration INT NOT NULL DEFAULT 0,
  last_sync_time BIGINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_scene_date (user_id, biz_scene, biz_date),
  KEY idx_scene_date (biz_scene, biz_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reward_flow (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id VARCHAR(64) NOT NULL,
  biz_scene VARCHAR(32) NOT NULL,
  prize_code VARCHAR(64) NOT NULL,
  prize_date DATE NOT NULL,
  prize_stage INT NOT NULL,
  prize_amount INT NOT NULL,
  out_biz_no VARCHAR(128) NOT NULL,
  rule_version VARCHAR(64) DEFAULT NULL,
  trace_id VARCHAR(64) DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_out_biz_no (out_biz_no),
  KEY idx_user_scene_date (user_id, biz_scene, prize_date),
  KEY idx_user_scene_date_stage (user_id, biz_scene, prize_date, prize_stage)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reward_outbox (
  event_id VARCHAR(64) NOT NULL,
  out_biz_no VARCHAR(128) NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  payload LONGTEXT NOT NULL,
  status TINYINT NOT NULL DEFAULT 0,
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_time DATETIME DEFAULT NULL,
  trace_id VARCHAR(64) DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (event_id),
  UNIQUE KEY uk_outbiz_eventtype (out_biz_no, event_type),
  KEY idx_status_next_retry (status, next_retry_time),
  KEY idx_trace_id (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
