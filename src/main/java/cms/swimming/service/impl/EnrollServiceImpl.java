package cms.swimming.service.impl;

import cms.swimming.domain.Enroll;
import cms.swimming.domain.Lesson;
import cms.swimming.domain.Locker;
import cms.swimming.domain.Payment;
import cms.swimming.dto.CancelRequestDto;
import cms.swimming.dto.EnrollRequestDto;
import cms.swimming.dto.EnrollResponseDto;
import cms.swimming.repository.EnrollRepository;
import cms.swimming.repository.LessonRepository;
import cms.swimming.repository.LockerRepository;
import cms.swimming.repository.PaymentRepository;
import cms.swimming.service.EnrollService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollServiceImpl implements EnrollService {

    private final EnrollRepository enrollRepository;
    private final LessonRepository lessonRepository;
    private final LockerRepository lockerRepository;
    private final PaymentRepository paymentRepository;

    @Override
    @Transactional
    public EnrollResponseDto createEnroll(Long userId, String userName, EnrollRequestDto enrollRequest, String ip) {
        // 1. 강습 조회 및 검증
        Lesson lesson = lessonRepository.findById(enrollRequest.getLessonId())
                .orElseThrow(() -> new EntityNotFoundException("강습을 찾을 수 없습니다. ID: " + enrollRequest.getLessonId()));
        
        // 1-1. 강습이 OPEN 상태인지 확인
        if (lesson.getStatus() != Lesson.LessonStatus.OPEN) {
            throw new IllegalStateException("신청 가능한 강습이 아닙니다. 상태: " + lesson.getStatus());
        }
        
        // 1-2. 신청 인원이 정원을 초과하는지 검증
        long currentEnrollments = lessonRepository.countCurrentEnrollments(enrollRequest.getLessonId());
        if (currentEnrollments >= lesson.getCapacity()) {
            throw new IllegalStateException("강습 정원이 초과되었습니다.");
        }
        
        // 1-3. 중복 신청 확인
        Optional<Enroll> existingEnroll = enrollRepository.findByUserIdAndLessonLessonIdAndStatus(
                userId, enrollRequest.getLessonId(), Enroll.EnrollStatus.APPLIED);
        if (existingEnroll.isPresent()) {
            throw new IllegalStateException("이미 신청한 강습입니다.");
        }
        
        // 1-4. 같은 달에 다른 강습 신청 여부 확인 (신규)
        long monthlyEnrollments = enrollRepository.countUserEnrollmentsByMonth(userId, lesson.getStartDate());
        if (monthlyEnrollments > 0) {
            throw new IllegalStateException("같은 달에 이미 다른 강습을 신청하셨습니다. 한 달에 한 개의 강습만 신청 가능합니다.");
        }
        
        // 2. 사물함 처리 (선택 사항)
        Locker locker = null;
        if (enrollRequest.getLockerId() != null) {
            locker = lockerRepository.findById(enrollRequest.getLockerId())
                    .orElseThrow(() -> new EntityNotFoundException("사물함을 찾을 수 없습니다. ID: " + enrollRequest.getLockerId()));
            
            // 2-1. 사물함이 활성화 상태인지 확인
            if (!locker.getIsActive()) {
                throw new IllegalStateException("사용할 수 없는 사물함입니다.");
            }
            
            // 2-2. 성별별 라커 용량 확인
            String gender = locker.getGender().name();
            long genderLockerCount = enrollRepository.countLockersByGender(lesson.getLessonId(), gender);
            
            if ("M".equals(gender) && genderLockerCount >= lesson.getMaleLockerCap()) {
                throw new IllegalStateException("남성 사물함 정원이 초과되었습니다.");
            } else if ("F".equals(gender) && genderLockerCount >= lesson.getFemaleLockerCap()) {
                throw new IllegalStateException("여성 사물함 정원이 초과되었습니다.");
            }
        }
        
        // 3. 신청 엔티티 생성 및 저장
        Enroll enroll = Enroll.builder()
                .userId(userId)
                .userName(userName)
                .lesson(lesson)
                .locker(locker)
                .status(Enroll.EnrollStatus.APPLIED)
                .createdBy(userName)
                .createdIp(ip)
                .updatedBy(userName)
                .updatedIp(ip)
                .build();
        
        Enroll savedEnroll = enrollRepository.save(enroll);
        
        // 4. 결제 정보 저장
        Payment payment = Payment.builder()
                .enroll(savedEnroll)
                .tid(enrollRequest.getPaymentInfo().getTid())
                .amount(enrollRequest.getPaymentInfo().getAmount())
                .status(Payment.PaymentStatus.PAID)
                .createdBy(userName)
                .createdIp(ip)
                .updatedBy(userName)
                .updatedIp(ip)
                .build();
        
        paymentRepository.save(payment);
        
        // 5. 수업 정원이 꽉 찼으면 자동으로 상태 CLOSED로 변경
        long updatedEnrollments = lessonRepository.countCurrentEnrollments(enrollRequest.getLessonId());
        if (updatedEnrollments >= lesson.getCapacity()) {
            lesson.updateStatus(Lesson.LessonStatus.CLOSED);
            lessonRepository.save(lesson);
        }
        
        return EnrollResponseDto.fromEntity(savedEnroll);
    }

    @Override
    @Transactional
    public EnrollResponseDto cancelEnroll(Long userId, Long enrollId, CancelRequestDto cancelRequest, String ip) {
        // 신청 정보 조회
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new EntityNotFoundException("신청 정보를 찾을 수 없습니다. ID: " + enrollId));
        
        // 본인 신청인지 확인
        if (!enroll.getUserId().equals(userId)) {
            throw new IllegalStateException("본인이 신청한 강습만 취소할 수 있습니다.");
        }
        
        // 이미 취소된 신청인지 확인
        if (enroll.getStatus() == Enroll.EnrollStatus.CANCELED) {
            throw new IllegalStateException("이미 취소된 신청입니다.");
        }
        
        // 강습 시작 여부에 따라 처리 방식 다름
        Lesson lesson = enroll.getLesson();
        boolean isLessonStarted = lesson.getStartDate().isBefore(java.time.LocalDate.now()) || 
                                 lesson.getStartDate().isEqual(java.time.LocalDate.now());
        
        if (isLessonStarted) {
            // 강습 시작 후 취소 요청 (관리자 승인 필요)
            enroll.updateStatus(Enroll.EnrollStatus.PENDING);
        } else {
            // 강습 시작 전 취소 (즉시 취소 처리)
            enroll.updateStatus(Enroll.EnrollStatus.CANCELED);
            
            // 결제 취소 처리
            Payment payment = paymentRepository.findByEnrollEnrollId(enrollId)
                    .orElseThrow(() -> new EntityNotFoundException("결제 정보를 찾을 수 없습니다. 신청 ID: " + enrollId));
            
            payment.cancelPayment(enroll.getUserName(), ip);
            paymentRepository.save(payment);
            
            // 수업 상태가 CLOSED였으면 다시 OPEN으로 변경 (자리가 생김)
            if (lesson.getStatus() == Lesson.LessonStatus.CLOSED) {
                lesson.updateStatus(Lesson.LessonStatus.OPEN);
                lessonRepository.save(lesson);
            }
        }
        
        Enroll updatedEnroll = enrollRepository.save(enroll);
        
        return EnrollResponseDto.fromEntity(updatedEnroll);
    }

    @Override
    public List<EnrollResponseDto> getUserEnrolls(Long userId) {
        return enrollRepository.findByUserId(userId).stream()
                .map(EnrollResponseDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public Page<EnrollResponseDto> getUserEnrollsByStatus(Long userId, String status, Pageable pageable) {
        Enroll.EnrollStatus enrollStatus = Enroll.EnrollStatus.valueOf(status);
        return enrollRepository.findByUserIdAndStatus(userId, enrollStatus, pageable)
                .map(EnrollResponseDto::fromEntity);
    }

    @Override
    public EnrollResponseDto getEnrollById(Long enrollId) {
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new EntityNotFoundException("신청 정보를 찾을 수 없습니다. ID: " + enrollId));
        return EnrollResponseDto.fromEntity(enroll);
    }

    @Override
    public Page<EnrollResponseDto> getEnrollsByLessonId(Long lessonId, Pageable pageable) {
        return enrollRepository.findByLessonLessonId(lessonId, pageable)
                .map(EnrollResponseDto::fromEntity);
    }
} 