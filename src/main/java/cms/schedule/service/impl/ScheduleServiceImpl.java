package cms.schedule.service.impl;

import cms.common.exception.BusinessException;
import cms.schedule.dto.ScheduleDto;
import cms.schedule.entity.Schedule;
import cms.schedule.repository.ScheduleRepository;
import cms.schedule.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {

    private final ScheduleRepository scheduleRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ScheduleDto> getSchedulesByDateRange(LocalDate dateFrom, LocalDate dateTo) {
        LocalDateTime startDateTime = dateFrom.atStartOfDay();
        LocalDateTime endDateTime = dateTo.atTime(23, 59, 59);
        return scheduleRepository.findByStartDateTimeBetween(startDateTime, endDateTime)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduleDto> getSchedulesByYearMonth(int year, int month) {
        LocalDateTime startDateTime = LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime endDateTime = startDateTime.plusMonths(1).minusSeconds(1);
        return scheduleRepository.findByStartDateTimeBetween(startDateTime, endDateTime)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ScheduleDto> searchSchedules(String title, String displayYn, Pageable pageable) {
        return scheduleRepository.search(title, displayYn, pageable)
                .map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public ScheduleDto getSchedule(Long scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .map(this::toDto)
                .orElseThrow(() -> new BusinessException("SCHEDULE_NOT_FOUND", "스케줄을 찾을 수 없습니다."));
    }

    @Override
    @Transactional
    public ScheduleDto createSchedule(ScheduleDto scheduleDto, String createdBy, String createdIp) {
        // 중복 체크
        if (scheduleRepository.existsByTitleAndStartDateTime(scheduleDto.getTitle(), scheduleDto.getStartDateTime())) {
            throw new BusinessException("SCHEDULE_DUPLICATE", "이미 등록된 스케줄입니다.");
        }

        Schedule schedule = Schedule.builder()
                .title(scheduleDto.getTitle())
                .content(scheduleDto.getContent())
                .startDateTime(scheduleDto.getStartDateTime())
                .endDateTime(scheduleDto.getEndDateTime())
                .displayYn(scheduleDto.getDisplayYn())
                .createdBy(createdBy)
                .createdIp(createdIp)
                .build();

        return toDto(scheduleRepository.save(schedule));
    }

    @Override
    @Transactional
    public ScheduleDto updateSchedule(Long scheduleId, ScheduleDto scheduleDto, String updatedBy, String updatedIp) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new BusinessException("SCHEDULE_NOT_FOUND", "스케줄을 찾을 수 없습니다."));

        // 중복 체크 (자신 제외)
        if (scheduleRepository.existsByTitleAndStartDateTimeAndScheduleIdNot(
                scheduleDto.getTitle(), scheduleDto.getStartDateTime(), scheduleId)) {
            throw new BusinessException("SCHEDULE_DUPLICATE", "이미 등록된 스케줄입니다.");
        }

        schedule.update(
                scheduleDto.getTitle(),
                scheduleDto.getContent(),
                scheduleDto.getStartDateTime(),
                scheduleDto.getEndDateTime(),
                scheduleDto.getDisplayYn(),
                updatedBy,
                updatedIp
        );

        return toDto(scheduleRepository.save(schedule));
    }

    @Override
    @Transactional
    public void deleteSchedule(Long scheduleId) {
        if (!scheduleRepository.existsById(scheduleId)) {
            throw new BusinessException("SCHEDULE_NOT_FOUND", "스케줄을 찾을 수 없습니다.");
        }
        scheduleRepository.deleteById(scheduleId);
    }

    private ScheduleDto toDto(Schedule schedule) {
        ScheduleDto dto = new ScheduleDto();
        dto.setScheduleId(schedule.getScheduleId());
        dto.setTitle(schedule.getTitle());
        dto.setContent(schedule.getContent());
        dto.setStartDateTime(schedule.getStartDateTime());
        dto.setEndDateTime(schedule.getEndDateTime());
        dto.setDisplayYn(schedule.getDisplayYn());
        dto.setStatus(calculateStatus(schedule));
        dto.setCreatedBy(schedule.getCreatedBy());
        dto.setCreatedDate(schedule.getCreatedDate());
        dto.setUpdatedBy(schedule.getUpdatedBy());
        dto.setUpdatedDate(schedule.getUpdatedDate());
        return dto;
    }

    private String calculateStatus(Schedule schedule) {
        if ("N".equals(schedule.getDisplayYn())) {
            return "HIDDEN";
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(schedule.getStartDateTime())) {
            return "UPCOMING";
        } else if (now.isAfter(schedule.getEndDateTime())) {
            return "ENDED";
        } else {
            return "ONGOING";
        }
    }
} 