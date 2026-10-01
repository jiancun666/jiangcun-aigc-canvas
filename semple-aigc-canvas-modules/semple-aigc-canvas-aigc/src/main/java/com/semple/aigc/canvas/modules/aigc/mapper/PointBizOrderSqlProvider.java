package com.semple.aigc.canvas.modules.aigc.mapper;

import java.util.Map;

/**
 * 为积分展示汇总生成无 XML 标签的参数化 SQL。
 */
public final class PointBizOrderSqlProvider {
    private PointBizOrderSqlProvider() {
    }

    public static String sumDisplayPoints(Map<String, Object> parameters) {
        String category = (String) parameters.get("category");
        StringBuilder sql;
        if ("CONSUMED".equals(category)) {
            sql = new StringBuilder("SELECT COALESCE(SUM(actual_points), 0) "
                    + "FROM aigc_point_biz_order WHERE deleted=1 AND account_id=#{accountId} "
                    + "AND order_status=3");
        } else if ("RETURNED".equals(category)) {
            sql = new StringBuilder("SELECT COALESCE(SUM(CASE WHEN order_status=4 THEN cost_points "
                    + "ELSE cost_points - actual_points END), 0) "
                    + "FROM aigc_point_biz_order WHERE deleted=1 AND account_id=#{accountId} "
                    + "AND (order_status=4 OR (order_status=3 AND cost_points > actual_points))");
        } else {
            throw new IllegalArgumentException("Unsupported point display category: " + category);
        }
        if (parameters.get("memberUserId") != null) {
            sql.append(" AND member_user_id=#{memberUserId}");
        }
        if (parameters.get("start") != null) {
            sql.append(" AND finished_at >= #{start}");
        }
        if (parameters.get("end") != null) {
            sql.append(" AND finished_at <= #{end}");
        }
        return sql.toString();
    }
}
