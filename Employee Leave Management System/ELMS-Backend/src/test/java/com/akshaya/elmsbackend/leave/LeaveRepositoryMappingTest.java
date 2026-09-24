package com.akshaya.elmsbackend.leave;

import com.akshaya.elmsbackend.employee.repository.EmployeeRepository;
import com.akshaya.elmsbackend.leave.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import javax.sql.DataSource;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LeaveRepositoryMappingTest {
    @Test void mappingsAndRepositoryQueriesValidateWithoutConnectingToDatabase() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setPackagesToScan("com.akshaya.elmsbackend");
        factory.setJpaPropertyMap(Map.of("hibernate.boot.allow_jdbc_metadata_access", false,
                "hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect",
                "hibernate.hbm2ddl.auto", "none"));
        try {
            factory.afterPropertiesSet();
            try (var entityManager = factory.getObject().createEntityManager()) {
                JpaRepositoryFactory repositories = new JpaRepositoryFactory(entityManager);
                assertNotNull(repositories.getRepository(EmployeeRepository.class));
                assertNotNull(repositories.getRepository(LeaveBalanceRepository.class));
                assertNotNull(repositories.getRepository(LeaveRequestRepository.class));
                assertNotNull(repositories.getRepository(LeaveApprovalRepository.class));
                assertNotNull(repositories.getRepository(CompanyHolidayRepository.class));
            }
            verify(dataSource, never()).getConnection();
        } finally {
            factory.destroy();
        }
    }
}
