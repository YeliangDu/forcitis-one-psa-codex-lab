-- D2（#934）：同步记录页面已删除，去掉它的导航项。
DELETE FROM platform_identity.navigation_items
 WHERE item_id = '019a0000-0000-7000-8000-000000000206' AND route_path = '/admin/sync-runs';
