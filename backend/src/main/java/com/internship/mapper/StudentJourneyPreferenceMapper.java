package com.internship.mapper;

import org.apache.ibatis.annotations.*;
import java.util.Map;

@Mapper
public interface StudentJourneyPreferenceMapper {
    @Select("SELECT route_choice AS routeChoice, seen, version FROM student_journey_preference WHERE student_id=#{id}")
    Map<String, Object> find(Long id);

    // Acquire an exclusive row lock even for an existing record. INSERT IGNORE holds a shared
    // duplicate-key lock, which can deadlock when two requests then upgrade to FOR UPDATE.
    @Insert("INSERT INTO student_journey_preference(student_id, seen, version) VALUES(#{id}, false, 0) ON DUPLICATE KEY UPDATE student_id=VALUES(student_id)")
    int ensure(Long id);

    @Select("SELECT route_choice AS routeChoice, seen, version FROM student_journey_preference WHERE student_id=#{id} FOR UPDATE")
    Map<String, Object> lock(Long id);

    @Update("UPDATE student_journey_preference SET route_choice=#{choice}, seen=true, version=version+1 WHERE student_id=#{id} AND version=#{version}")
    int update(@Param("id") Long id, @Param("choice") String choice, @Param("version") long version);
}
