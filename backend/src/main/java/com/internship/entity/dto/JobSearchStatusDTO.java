package com.internship.entity.dto;

/** 由既有实习状态和求职过程记录派生的只读展示状态。 */
public record JobSearchStatusDTO(String jobSearchStatus, String jobSearchStatusText) {
}
