package com.priyatra.guide.data.db

import androidx.room.Dao
import androidx.room.Query

@Dao
interface CustomerPhoneDao {
    /**
     * Match normalized digits, including last-10 (India-style) as in [com.priyatra.guide.auth.PhoneUtils].
     */
    @Query(
        """
        SELECT * FROM customer_phones
        WHERE phone_digits = :n
        OR (LENGTH(phone_digits) >= 10 AND LENGTH(:n) >= 10
            AND SUBSTR(phone_digits, -10) = SUBSTR(:n, -10))
        LIMIT 1
        """,
    )
    fun findFirstByNormalizedDigits(n: String): CustomerPhoneEntity?
}
