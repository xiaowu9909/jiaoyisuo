package com.vaultpi.config;

import com.vaultpi.asset.entity.Coin;
import com.vaultpi.asset.repository.CoinRepository;
import com.vaultpi.content.entity.Announcement;
import com.vaultpi.content.entity.SysHelp;
import com.vaultpi.content.repository.AnnouncementRepository;
import com.vaultpi.content.repository.SysHelpRepository;
import com.vaultpi.market.entity.ExchangeCoin;
import com.vaultpi.market.repository.ExchangeCoinRepository;
import com.vaultpi.system.entity.SystemConfig;
import com.vaultpi.system.repository.SystemConfigRepository;
import com.vaultpi.user.entity.Member;
import com.vaultpi.user.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@Order(1)
public class DataInitializer implements ApplicationRunner {

    /** 生产环境应设为 false，避免自动创建默认 admin/admin123；首次部署后请通过其他途径创建管理员 */
    @Value("${vaultpi.bootstrap.create-default-admin:true}")
    private boolean createDefaultAdmin;

    private final CoinRepository coinRepository;
    private final ExchangeCoinRepository exchangeCoinRepository;
    private final AnnouncementRepository announcementRepository;
    private final SysHelpRepository sysHelpRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final SystemConfigRepository systemConfigRepository;

    public DataInitializer(CoinRepository coinRepository,
                           ExchangeCoinRepository exchangeCoinRepository,
                           AnnouncementRepository announcementRepository,
                           SysHelpRepository sysHelpRepository,
                           MemberRepository memberRepository,
                           PasswordEncoder passwordEncoder,
                           SystemConfigRepository systemConfigRepository) {
        this.coinRepository = coinRepository;
        this.exchangeCoinRepository = exchangeCoinRepository;
        this.announcementRepository = announcementRepository;
        this.sysHelpRepository = sysHelpRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.systemConfigRepository = systemConfigRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        memberRepository.findByUsername("admin").ifPresentOrElse(admin -> {
            // 已存在的 admin 会员：内部用户、默认已完成实名认证
            boolean changed = false;
            if (!"INTERNAL".equals(admin.getUserType())) {
                admin.setUserType("INTERNAL");
                changed = true;
            }
            if (admin.getRealName() == null || admin.getRealName().isBlank()) {
                admin.setRealName("管理员");
                changed = true;
            }
            if (changed) memberRepository.save(admin);
        }, () -> {
            if (!createDefaultAdmin) {
                return; // 生产环境不自动创建默认管理员，需通过脚本或首次部署流程创建
            }
            Member admin = new Member();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setEmail("admin@vault314.com");
            admin.setStatus("NORMAL");
            admin.setRole("ADMIN");
            admin.setUserType("INTERNAL"); // 管理员为内部用户，不参与业绩统计
            admin.setRealName("管理员");   // 默认已完成实名认证
            admin.setRegistrationTime(Instant.now());
            memberRepository.save(admin);
        });
        if (coinRepository.count() == 0) {
            List<Coin> defaults = List.of(
                coin("BTC", "Bitcoin"),
                coin("USDT", "Tether USDT"),
                coin("ETH", "Ethereum")
            );
            coinRepository.saveAll(defaults);
        }
        if (exchangeCoinRepository.count() == 0) {
            exchangeCoinRepository.saveAll(List.of(
                exCoin("BTC/USDT", "USDT", "BTC"),
                exCoin("ETH/USDT", "USDT", "ETH")
            ));
        }
        if (announcementRepository.count() == 0) {
            Announcement a1 = new Announcement();
            a1.setTitle("欢迎使用 Vault π");
            a1.setContent("Vault π 已切换至全新架构（Vault314.com）。");
            a1.setLang("CN");
            announcementRepository.save(a1);
            Announcement a2 = new Announcement();
            a2.setTitle("安全优先，长期主义");
            a2.setContent("建议及时开启二次验证并妥善保管 API Key。");
            a2.setLang("CN");
            announcementRepository.save(a2);
        }
        if (sysHelpRepository.count() == 0) {
            SysHelp h1 = new SysHelp();
            h1.setTitle("新手入门");
            h1.setClassification("新手入门");
            h1.setContent("欢迎使用 Vault π。请先完成注册与登录，然后可在「币币交易」进行买卖。");
            h1.setLang("CN");
            h1.setSort(0);
            sysHelpRepository.save(h1);
            SysHelp h2 = new SysHelp();
            h2.setTitle("如何充币");
            h2.setClassification("充值指南");
            h2.setContent("进入个人中心 - 充币，选择币种后即可查看充币地址与二维码。请勿向该地址充值非对应币种。");
            h2.setLang("CN");
            h2.setSort(1);
            sysHelpRepository.save(h2);
            SysHelp h3 = new SysHelp();
            h3.setTitle("如何提币");
            h3.setClassification("提现指南");
            h3.setContent("进入个人中心 - 提现，先添加提现地址，再提交提现申请。审核通过后将会打款。");
            h3.setLang("CN");
            h3.setSort(2);
            sysHelpRepository.save(h3);
        }
        // 首页「新手入门」区块配置（B 端可修改）
        if (systemConfigRepository.findById("home_getting_start").isEmpty()) {
            String defaultJson = """
                {"title":"新手入门 | 极速买币","subtitle":"Vault π 官方新手入门辅助通道","items":[{"name":"法币通道","tips":"用人民币买卖比特币等"},{"name":"交易入门","tips":"新手币币交易基础入门"},{"name":"区块链基础","tips":"区块链、比特币基础入门"},{"name":"新人社群","tips":"经验交流、信息共享"}]}
                """.trim().replace("\n", "");
            SystemConfig sc = new SystemConfig();
            sc.setId("home_getting_start");
            sc.setValue(defaultJson);
            sc.setRemark("首页新手入门区块：标题、副标题、四个卡片名称与描述");
            sc.setGroupName("首页配置");
            systemConfigRepository.save(sc);
        }
        // 首页「关于 Vault π」区块配置（B 端可修改）
        if (systemConfigRepository.findById("home_about_brand").isEmpty()) {
            String defaultAbout = "{\"title\":\"关于 Vault π\",\"detail\":\"诚实 | 公平 | 热情 | 开放\",\"desc1\":\"Vault π 由一群专注数字资产与安全技术的从业者发起，核心团队长期深耕交易系统与风控基础设施。\",\"desc2\":\"BIZZAN.COM定位于区块链基础服务商，致力于为全球用户提供优质加密资产交易平台，秉承着\\\"不作恶\\\"的基本原则，坚持诚实、公正、热情的服务于客户，以开放的态度迎接一切有利于用户根本利益的伙伴/项目。\"}";
            SystemConfig about = new SystemConfig();
            about.setId("home_about_brand");
            about.setValue(defaultAbout);
            about.setRemark("首页关于我们区块：标题、副标题、两段描述");
            about.setGroupName("首页配置");
            systemConfigRepository.save(about);
        }
        // 首页「扫描二维码，下载APP」区块配置（B 端可修改）
        if (systemConfigRepository.findById("home_app_download").isEmpty()) {
            String defaultDownload = "{\"scanText\":\"扫描二维码，下载APP\",\"downloadText\":\"立即下载\",\"slogan\":\"Vault π App - 全球数字资产交易平台\",\"imageUrl\":\"\"}";
            SystemConfig dl = new SystemConfig();
            dl.setId("home_app_download");
            dl.setValue(defaultDownload);
            dl.setRemark("首页下载APP区块：扫码提示、下载按钮文案、slogan");
            dl.setGroupName("首页配置");
            systemConfigRepository.save(dl);
        }
        // C 端全局 Logo（B 端可修改，留空则 C 端用默认 /images/logo.png 与 /images/logo-bottom.png）
        if (systemConfigRepository.findById("site_logo").isEmpty()) {
            String defaultLogo = "{\"headerLogoUrl\":\"\",\"footerLogoUrl\":\"\"}";
            SystemConfig logo = new SystemConfig();
            logo.setId("site_logo");
            logo.setValue(defaultLogo);
            logo.setRemark("C 端全局 Logo：头部、底部图片地址，留空使用默认");
            logo.setGroupName("首页配置");
            systemConfigRepository.save(logo);
        }
    }

    private Coin coin(String unit, String name) {
        Coin c = new Coin();
        c.setUnit(unit);
        c.setName(name);
        c.setEnable(true);
        return c;
    }

    private ExchangeCoin exCoin(String symbol, String base, String coin) {
        ExchangeCoin e = new ExchangeCoin();
        e.setSymbol(symbol);
        e.setBaseSymbol(base);
        e.setCoinSymbol(coin);
        e.setEnable(true);
        return e;
    }
}
