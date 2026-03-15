package com.learnflow.repository;

import com.learnflow.entity.UserResourceFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 用户资源反馈表的基础仓库。
 *
 * 当前版本除了保存记录外，还提供按资源维度的简单聚合查询，
 * 用于资源质量看板展示平均评分与举报次数等。
 */
public interface UserResourceFeedbackRepository extends JpaRepository<UserResourceFeedback, Long> {

    @Query("""
            select f.resource.id as resourceId,
                   avg(coalesce(f.rating, 0)) as avgRating,
                   count(f.id) as feedbackCount,
                   sum(case when f.reportedInvalid = true then 1 else 0 end) as invalidReportCount
            from UserResourceFeedback f
            group by f.resource.id
            """)
    List<Object[]> aggregateByResource();

    @Query(value = """
            SELECT date(created_at) as day,
                   AVG(rating) as avgRating,
                   COUNT(*) as feedbackCount,
                   SUM(CASE WHEN is_reported_invalid THEN 1 ELSE 0 END) as invalidReportCount
            FROM user_resource_feedback
            WHERE created_at >= current_date - :days
            GROUP BY date(created_at)
            ORDER BY day ASC
            """, nativeQuery = true)
    List<Object[]> aggregateDaily(@Param("days") int days);

    @Query(value = """
            SELECT id, rating, comment, is_reported_invalid, created_at, user_id
            FROM user_resource_feedback
            WHERE resource_bank_id = :resourceId
            ORDER BY created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findRecentByResource(@Param("resourceId") Long resourceId, @Param("limit") int limit);


    @Query("""
            select f.resource.id as resourceId,
                   avg(coalesce(f.rating, 0)) as avgRating,
                   count(f.id) as feedbackCount,
                   sum(case when f.reportedInvalid = true then 1 else 0 end) as invalidReportCount
            from UserResourceFeedback f
            where f.resource.id in :resourceIds
            group by f.resource.id
            """)
    List<Object[]> aggregateByResourceIds(@Param("resourceIds") List<Long> resourceIds);

    List<UserResourceFeedback> findByUser_IdAndResource_IdInOrderByCreatedAtDesc(Long userId, List<Long> resourceIds);

    java.util.Optional<UserResourceFeedback> findTopByUser_IdAndResource_IdOrderByCreatedAtDesc(Long userId, Long resourceId);
}
