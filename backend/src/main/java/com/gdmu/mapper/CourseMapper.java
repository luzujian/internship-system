package com.gdmu.mapper;

import com.gdmu.entity.Course;
import com.gdmu.entity.CourseStudent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.time.LocalDateTime;

@Mapper
public interface CourseMapper {
    // 根据ID查询课程
    Course findById(Long id);
    
    // 查询所有课程
    List<Course> findAll();
    
    // 新增课程
    int insert(Course course);
    
    // 更新课程
    int update(Course course);
    
    // 删除课程
    int delete(Long id);
    
    // 根据教师ID查询课程
    List<Course> findByTeacherId(Long teacherId);

    // 根据专业ID查询课程
    List<Course> findByMajorId(Long majorId);
    
    // 分页条件查询课程
    List<Course> list(String name, Integer grade, Long majorId, Long teacherId, LocalDateTime begin, LocalDateTime end);
    
    // 支持多专业ID查询的条件查询课程
    List<Course> listWithMultiMajorIds(String name, Integer grade, String majorIds, Long teacherId, LocalDateTime begin, LocalDateTime end);
    
    // ======== 课程学生关联相关方法 ========
    
    // 根据课程ID查询课程学生关联
    List<CourseStudent> findCourseStudentsByCourseId(Long courseId);
    
    // 根据课程ID统计学生数量
    Integer countStudentsByCourseId(Long courseId);
    
    // 根据学生ID查询课程学生关联
    List<CourseStudent> findCourseStudentsByStudentId(Long studentId);
    
    // 根据课程ID和学生ID查询课程学生关联
    CourseStudent findCourseStudentByCourseIdAndStudentId(@Param("courseId") Long courseId, @Param("studentId") Long studentId);
    
    // 插入课程学生关联
    int insertCourseStudent(CourseStudent courseStudent);
    
    // 更新课程学生关联
    int updateCourseStudent(CourseStudent courseStudent);
    
    // 删除课程学生关联
    int deleteCourseStudent(@Param("courseId") Long courseId, @Param("studentId") Long studentId);
    
    // 根据课程ID删除所有课程学生关联
    int deleteCourseStudentsByCourseId(Long courseId);
    
    // 根据学生ID删除所有课程学生关联
    int deleteCourseStudentsByStudentId(Long studentId);
    
    // 批量插入课程学生关联
    int batchInsertCourseStudents(@Param("courseStudents") List<CourseStudent> courseStudents);
    
    // 根据ID列表查询课程学生关联
    List<CourseStudent> findCourseStudentsByIds(@Param("ids") List<Long> ids);
    
    // 根据ID查询课程学生关联
    CourseStudent findCourseStudentById(Long id);
    
    // 根据课程ID列表查询课程学生关联数量
    Integer countCourseStudentsByCourseIds(@Param("courseIds") List<Long> courseIds);
}