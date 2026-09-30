-- V002：初始管理员（docs/04 第三节）。
-- 用户名 admin，密码 admin。这是公开的开发期默认账号，登录页会明文提示；上线前修改密码（docs/08 阶段 7）。
-- 这里只存 BCrypt 哈希（cost 10），不存明文。

INSERT INTO SYS_USER (USERNAME, PASSWORD_HASH, DISPLAY_NAME, STATUS)
VALUES ('admin', '$2a$10$jD7olFcekYAiLtcUz.Gwt.4VrKQjkR7/vq2hlUrB0VCSCXd5.iWFm', 'Admin', 1);
