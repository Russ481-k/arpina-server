package cms.mypage.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RenewalRequestDto {
  private Long lessonId;
  // carryLocker and existingLockerIdToCarry might be obsolete if individual
  // lockers aren't managed.
  // Replaced by a simpler wantsLocker concept for renewal.
  // private boolean carryLocker;
  // private Long existingLockerIdToCarry;
  private boolean usesLocker; // User wants a locker (from inventory) for the renewed period.
  // private boolean wantsNewLocker; // This is now covered by wantsLocker
}