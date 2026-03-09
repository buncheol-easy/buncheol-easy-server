package buncheoleasy.payment.infrastructure;

import buncheoleasy.payment.domain.Payment;
import buncheoleasy.payment.domain.PaymentPhase;
import buncheoleasy.payment.domain.PaymentStatus;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentMapper {

  void insert(Payment payment);

  int update(
      @Param("payment") Payment payment, @Param("expectedStatus") PaymentStatus expectedStatus);

  Optional<Payment> findByOrderId(@Param("orderId") String orderId);

  Optional<Payment> findLatestPaymentByParticipationIdAndPaymentPhase(
      @Param("participationId") Long participationId,
      @Param("paymentPhase") PaymentPhase paymentPhase);
}
