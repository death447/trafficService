package com.example.backend.mapper;

import com.example.backend.entity.DispatchOrderEvaluation;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DispatchOrderEvaluationMapper {

    @Select("SELECT * FROM dispatch_order_evaluation WHERE dispatch_order_id = #{orderId}")
    DispatchOrderEvaluation findByOrderId(Long orderId);

    @Select({
            "<script>",
            "SELECT dispatch_order_id FROM dispatch_order_evaluation",
            "WHERE dispatch_order_id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    List<Long> findOrderIds(@Param("ids") List<Long> ids);

    @Insert("INSERT INTO dispatch_order_evaluation (dispatch_order_id, rater_user_id, score_punctual, "
            + "score_standard, score_safety, score_attitude, comment) VALUES (#{dispatchOrderId}, "
            + "#{raterUserId}, #{scorePunctual}, #{scoreStandard}, #{scoreSafety}, #{scoreAttitude}, #{comment})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DispatchOrderEvaluation row);
}
