package com.fsad.mutualfund.config;

import com.fsad.mutualfund.entity.*;
import com.fsad.mutualfund.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Random;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final InvestorProfileRepository investorProfileRepository;
    private final AdvisorProfileRepository advisorProfileRepository;
    private final MutualFundRepository fundRepository;
    private final NavHistoryRepository navHistoryRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository,
                      InvestorProfileRepository investorProfileRepository,
                      AdvisorProfileRepository advisorProfileRepository,
                      MutualFundRepository fundRepository,
                      NavHistoryRepository navHistoryRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.investorProfileRepository = investorProfileRepository;
        this.advisorProfileRepository = advisorProfileRepository;
        this.fundRepository = fundRepository;
        this.navHistoryRepository = navHistoryRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return; // Already seeded
        }

        System.out.println("🌱 Seeding database with mock data...");

        seedUsers();
        seedFunds();

        System.out.println("✅ Database seeding complete!");
    }

    private void seedUsers() {
        // Admin
        User admin = User.builder()
                .email("admin@mutualfund.com")
                .password(passwordEncoder.encode("admin123"))
                .fullName("System Administrator")
                .role(User.Role.ADMIN)
                .authProvider(User.AuthProvider.LOCAL)
                .verified(true)
                .build();
        userRepository.save(admin);

        // Investor (demo account)
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

        // Advisor 1
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

        // Advisor 2
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

        // Analyst
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

    private void seedFunds() {
        Random random = new Random(42); // Fixed seed for reproducible data

        // Fund 1: Aggressive Equity
        createFundWithHistory("Nifty Growth Equity Fund", "NGEF", MutualFund.Category.EQUITY,
                new BigDecimal("1.45"), 4, new BigDecimal("345.7800"),
                "Arun Mehta", "High-growth equity fund tracking India's top 50 companies. Ideal for aggressive investors with 5+ year horizons.",
                new BigDecimal("5000.0000"), 345.78, 0.0012, 0.025, random);

        // Fund 2: Conservative Debt
        createFundWithHistory("Secure Bond Income Fund", "SBIF", MutualFund.Category.DEBT,
                new BigDecimal("0.65"), 2, new BigDecimal("112.3400"),
                "Meera Kapoor", "Low-risk government and corporate bond fund providing stable income. Suitable for conservative investors seeking capital preservation.",
                new BigDecimal("1000.0000"), 112.34, 0.0003, 0.005, random);

        // Fund 3: Moderate Hybrid
        createFundWithHistory("Balanced Advantage Fund", "BADF", MutualFund.Category.HYBRID,
                new BigDecimal("1.10"), 3, new BigDecimal("228.5600"),
                "Rajesh Kumar", "Dynamic asset allocation fund that adjusts equity-debt mix based on market valuations. Perfect for moderate-risk investors.",
                new BigDecimal("2500.0000"), 228.56, 0.0007, 0.015, random);

        // Fund 4: Tax Saving ELSS
        createFundWithHistory("Tax Shield ELSS Fund", "TSEF", MutualFund.Category.ELSS,
                new BigDecimal("1.35"), 4, new BigDecimal("189.2300"),
                "Sanjay Gupta", "Equity-linked savings scheme with 3-year lock-in. Offers tax benefits under Section 80C with high growth potential.",
                new BigDecimal("500.0000"), 189.23, 0.0010, 0.022, random);

        // Fund 5: Low-risk Equity
        createFundWithHistory("Blue Chip Value Fund", "BCVF", MutualFund.Category.EQUITY,
                new BigDecimal("0.95"), 2, new BigDecimal("476.1200"),
                "Deepika Raman", "Large-cap value fund investing in blue-chip stocks with proven track records. Lower volatility than mid/small-cap funds.",
                new BigDecimal("3000.0000"), 476.12, 0.0005, 0.012, random);
    }

    private void createFundWithHistory(String name, String ticker, MutualFund.Category category,
                                        BigDecimal expenseRatio, int riskRating, BigDecimal currentNav,
                                        String manager, String description, BigDecimal minInvestment,
                                        double startNav, double dailyDrift, double dailyVolatility,
                                        Random random) {
        MutualFund fund = MutualFund.builder()
                .fundName(name)
                .tickerSymbol(ticker)
                .category(category)
                .expenseRatio(expenseRatio)
                .riskRating(riskRating)
                .currentNav(currentNav)
                .fundManager(manager)
                .description(description)
                .minInvestment(minInvestment)
                .build();
        fund = fundRepository.save(fund);

        // Generate 365 days of NAV history using Random Walk
        double nav = startNav;
        LocalDate date = LocalDate.now().minusDays(365);

        for (int i = 0; i < 365; i++) {
            double change = dailyDrift + dailyVolatility * random.nextGaussian();
            nav = nav * (1.0 + change);
            nav = Math.max(nav, startNav * 0.5); // Floor at 50% of start

            NavHistory history = NavHistory.builder()
                    .mutualFund(fund)
                    .navDate(date.plusDays(i))
                    .navValue(BigDecimal.valueOf(nav).setScale(4, RoundingMode.HALF_UP))
                    .build();
            navHistoryRepository.save(history);
        }

        // Update current NAV to latest
        fund.setCurrentNav(BigDecimal.valueOf(nav).setScale(4, RoundingMode.HALF_UP));
        fundRepository.save(fund);
    }
}
