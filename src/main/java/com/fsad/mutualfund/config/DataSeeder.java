package com.fsad.mutualfund.config;

import com.fsad.mutualfund.entity.AdvisorProfile;
import com.fsad.mutualfund.entity.InvestorProfile;
import com.fsad.mutualfund.entity.User;
import com.fsad.mutualfund.repository.AdvisorProfileRepository;
import com.fsad.mutualfund.repository.InvestorProfileRepository;
import com.fsad.mutualfund.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final InvestorProfileRepository investorProfileRepository;
    private final AdvisorProfileRepository advisorProfileRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository,
                      InvestorProfileRepository investorProfileRepository,
                      AdvisorProfileRepository advisorProfileRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.investorProfileRepository = investorProfileRepository;
        this.advisorProfileRepository = advisorProfileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }

        System.out.println("Seeding database with demo users...");
        seedUsers();
        System.out.println("Database seeding complete.");
    }

    private void seedUsers() {
        User admin = User.builder()
                .email("admin@mutualfund.com")
                .password(passwordEncoder.encode("admin123"))
                .fullName("System Administrator")
                .role(User.Role.ADMIN)
                .authProvider(User.AuthProvider.LOCAL)
                .verified(true)
                .build();
        userRepository.save(admin);

        User investor = User.builder()
                .email("investor@demo.com")
                .password(passwordEncoder.encode("investor123"))
                .fullName("Rahul Sharma")
                .role(User.Role.INVESTOR)
                .authProvider(User.AuthProvider.LOCAL)
                .verified(true)
                .build();
        investor = userRepository.save(investor);

        InvestorProfile investorProfile = InvestorProfile.builder()
                .user(investor)
                .riskToleranceScore(55)
                .riskCategory(InvestorProfile.RiskCategory.MODERATE)
                .walletBalance(new BigDecimal("250000.0000"))
                .investmentHorizon("3-5 years")
                .build();
        investorProfileRepository.save(investorProfile);

        User advisor1 = User.builder()
                .email("advisor@demo.com")
                .password(passwordEncoder.encode("advisor123"))
                .fullName("Dr. Priya Patel")
                .role(User.Role.ADVISOR)
                .authProvider(User.AuthProvider.LOCAL)
                .verified(true)
                .build();
        advisor1 = userRepository.save(advisor1);

        AdvisorProfile advisorProfile1 = AdvisorProfile.builder()
                .user(advisor1)
                .specialization("Retirement Planning & Wealth Management")
                .consultationFee(new BigDecimal("75.00"))
                .experienceYears(12)
                .bio("Certified Financial Planner with 12+ years of experience in retirement planning and wealth management. Specialized in long-term portfolio construction for risk-averse investors.")
                .averageRating(4.8)
                .totalReviews(156)
                .build();
        advisorProfileRepository.save(advisorProfile1);

        User advisor2 = User.builder()
                .email("advisor2@demo.com")
                .password(passwordEncoder.encode("advisor123"))
                .fullName("Vikram Singh")
                .role(User.Role.ADVISOR)
                .authProvider(User.AuthProvider.LOCAL)
                .verified(true)
                .build();
        advisor2 = userRepository.save(advisor2);

        AdvisorProfile advisorProfile2 = AdvisorProfile.builder()
                .user(advisor2)
                .specialization("Tax Saving & ELSS Investments")
                .consultationFee(new BigDecimal("50.00"))
                .experienceYears(8)
                .bio("Expert in tax-saving mutual fund strategies with a focus on ELSS funds. Helps clients optimize their Section 80C investments while maximizing returns.")
                .averageRating(4.5)
                .totalReviews(89)
                .build();
        advisorProfileRepository.save(advisorProfile2);

        User analyst = User.builder()
                .email("analyst@demo.com")
                .password(passwordEncoder.encode("analyst123"))
                .fullName("Anita Desai")
                .role(User.Role.ANALYST)
                .authProvider(User.AuthProvider.LOCAL)
                .verified(true)
                .build();
        userRepository.save(analyst);
    }
}
