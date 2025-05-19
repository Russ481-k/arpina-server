package cms.locker.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "locker_inventory")
@Getter
@Setter
@NoArgsConstructor
public class LockerInventory {
    @Id
    @Column(length = 10)
    private String gender; // "MALE", "FEMALE"

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity = 0;

    @Column(name = "used_quantity", nullable = false)
    private int usedQuantity = 0;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Transient method to get available quantity
    public int getAvailableQuantity() {
        return totalQuantity - usedQuantity;
    }
} 