package com.example.backend.mapper;

import com.example.backend.dto.ReportDateCount;
import com.example.backend.dto.ReportLotDetain;
import com.example.backend.dto.ReportOrderRow;
import com.example.backend.dto.ReportQualityAvg;
import com.example.backend.dto.ReportRescuerQuality;
import com.example.backend.dto.ReportStatusCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReportMapper {

    @Select("SELECT COUNT(*) FROM dispatch_order WHERE create_time >= #{start} AND create_time < #{end}")
    long countOrders(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT status, COUNT(*) AS count FROM dispatch_order "
            + "WHERE create_time >= #{start} AND create_time < #{end} GROUP BY status")
    List<ReportStatusCount> countOrdersByStatus(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS date, COUNT(*) AS count FROM dispatch_order "
            + "WHERE create_time >= #{start} AND create_time < #{end} GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d')")
    List<ReportDateCount> countOrdersByDay(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT o.id, o.order_no AS orderNo, o.accident_address AS accidentAddress, o.status, o.plate_no AS plateNo, "
            + "COALESCE(NULLIF(u.real_name, ''), u.username) AS rescuerName, "
            + "o.create_time AS createTime, o.dispatched_at AS dispatchedAt, o.completed_at AS completedAt "
            + "FROM dispatch_order o LEFT JOIN user u ON u.id = o.rescuer_id "
            + "WHERE o.create_time >= #{start} AND o.create_time < #{end} "
            + "ORDER BY o.create_time DESC, o.id DESC")
    List<ReportOrderRow> listOrders(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(*) AS ratedCount, AVG(e.score_punctual) AS avgPunctual, AVG(e.score_standard) AS avgStandard, "
            + "AVG(e.score_safety) AS avgSafety, AVG(e.score_attitude) AS avgAttitude "
            + "FROM dispatch_order o INNER JOIN dispatch_order_evaluation e ON e.dispatch_order_id = o.id "
            + "WHERE o.create_time >= #{start} AND o.create_time < #{end}")
    ReportQualityAvg selectQualityAvg(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT o.rescuer_id AS rescuerId, "
            + "COALESCE(NULLIF(u.real_name, ''), u.username, '未指定') AS rescuerName, "
            + "COUNT(*) AS ratedCount, AVG(e.score_punctual) AS avgPunctual, AVG(e.score_standard) AS avgStandard, "
            + "AVG(e.score_safety) AS avgSafety, AVG(e.score_attitude) AS avgAttitude "
            + "FROM dispatch_order o INNER JOIN dispatch_order_evaluation e ON e.dispatch_order_id = o.id "
            + "LEFT JOIN user u ON u.id = o.rescuer_id "
            + "WHERE o.create_time >= #{start} AND o.create_time < #{end} "
            + "GROUP BY o.rescuer_id, COALESCE(NULLIF(u.real_name, ''), u.username, '未指定')")
    List<ReportRescuerQuality> listQualityByRescuer(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(*) FROM detained_vehicle WHERE in_time >= #{start} AND in_time < #{end}")
    long countDetainInbound(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(*) FROM detained_vehicle WHERE in_time >= #{start} AND in_time < #{end} AND status = 'IN_YARD'")
    long countDetainInYard(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(*) FROM detained_vehicle WHERE out_time >= #{start} AND out_time < #{end}")
    long countDetainOutbound(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT p.id AS parkingLotId, p.name AS parkingLotName, "
            + "SUM(CASE WHEN d.in_time >= #{start} AND d.in_time < #{end} THEN 1 ELSE 0 END) AS inbound, "
            + "SUM(CASE WHEN d.in_time >= #{start} AND d.in_time < #{end} AND d.status = 'IN_YARD' THEN 1 ELSE 0 END) AS inYard, "
            + "SUM(CASE WHEN d.out_time >= #{start} AND d.out_time < #{end} THEN 1 ELSE 0 END) AS outbound "
            + "FROM detained_vehicle d INNER JOIN parking_lot p ON p.id = d.parking_lot_id "
            + "WHERE (d.in_time >= #{start} AND d.in_time < #{end}) OR (d.out_time >= #{start} AND d.out_time < #{end}) "
            + "GROUP BY p.id, p.name")
    List<ReportLotDetain> listDetainByLot(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
