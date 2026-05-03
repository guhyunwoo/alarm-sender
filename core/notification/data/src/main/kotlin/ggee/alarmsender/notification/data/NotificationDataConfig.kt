package ggee.alarmsender.notification.data

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/**
 * 데이터 레이어 빈 (Repository 구현체) 일괄 등록용 진입 Config.
 * 부트스트랩 또는 통합 테스트는 이 클래스만 @Import 하면 된다.
 */
@Configuration
@ComponentScan(basePackageClasses = [NotificationDataConfig::class])
class NotificationDataConfig
