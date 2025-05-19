package cms.locker.service;
 
public interface LockerService {
    int getAvailableLockerCount(String gender);
    boolean assignLocker(String gender);
    void releaseLocker(String gender);
} 