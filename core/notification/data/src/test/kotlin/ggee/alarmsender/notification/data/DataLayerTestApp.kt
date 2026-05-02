package ggee.alarmsender.notification.data

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * @DataJpaTest 가 의존하는 SpringBootConfiguration. 통합 테스트 한정 컨텍스트.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackageClasses = [DataLayerTestApp::class])
@EntityScan(basePackageClasses = [DataLayerTestApp::class])
@EnableJpaRepositories(basePackageClasses = [DataLayerTestApp::class])
class DataLayerTestApp
