package io.elasticore.base.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

import io.elasticore.base.model.pub.rules.RulesConfig;
import java.util.regex.Pattern;

import io.elasticore.base.util.ConsoleLog;

/**
 * ElastiCORE Domain Initialization Helper
 * 
 * Provides automatic domain environment setup with interactive configuration
 * for uninitialized ElastiCORE projects.
 * 
 * @author ElastiCORE Framework
 * @version 1.0
 */
public class InitializationHelper {
    
    private static final String BLUEPRINT_DIR = "src/main/resources/blueprint";
    private static final String ENV_FILE = "env.yml";
    private static final String MODEL_DIR = "model";
    private static final String MODEL_FILE = "model.yml";
    
    // Validation patterns
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9-]*$");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9]*(?:\\.[a-zA-Z][a-zA-Z0-9]*)*$");
    
    /**
     * The shapes a new domain can be started in.
     *
     * Before 3.0.260829 the wizard wrote a 2.x env.yml and named no bundle at
     * all, so a project created today generated what 2.x generated and its
     * author had no way to learn from their own configuration that anything
     * else existed. The rule engine was reachable only by someone who had
     * already read the guide - which is the wrong way round, since the guide
     * is what the generated file should be sending them to.
     *
     * LAYERED stays the default. Choosing an architecture is a decision about
     * a codebase and a wizard is not the place to make one on somebody's
     * behalf; it is the place to show that the decision exists.
     */
    private static final int LAYERED = 0;
    private static final int DOMAIN_MODEL = 1;
    private static final int HEXAGONAL = 2;

    /**
     * The architectures, in the order they are offered.
     *
     * The names and the bundles they map to live in RulesConfig, not here.
     * They were here first, and a second copy appeared in the rule runner's
     * defaults - two tables for one decision, which is how a wizard and a
     * build come to disagree about what a project is.
     */
    private static final RulesConfig.Architecture[] ARCHITECTURES = {
        RulesConfig.Architecture.LAYERED,
        RulesConfig.Architecture.DOMAIN_MODEL,
        RulesConfig.Architecture.HEXAGONAL
    };

    private static String label(int architecture) {
        return RulesConfig.describe(ARCHITECTURES[architecture]);
    }

    private static String bundle(int architecture, boolean kotlin) {
        return RulesConfig.bundleFor(ARCHITECTURES[architecture],
                kotlin ? RulesConfig.Language.KOTLIN : RulesConfig.Language.JAVA);
    }

    private final Scanner scanner;
    
    public InitializationHelper() {
        this.scanner = new Scanner(System.in);
    }
    
    /**
     * Check if ElastiCORE environment is initialized
     */
    public static boolean isInitialized() {
        File blueprintDir = new File(BLUEPRINT_DIR);
        if (!blueprintDir.exists()) {
            return false;
        }
        
        // Check if any domain directory with env.yml exists
        File[] domainDirs = blueprintDir.listFiles(File::isDirectory);
        if (domainDirs == null || domainDirs.length == 0) {
            return false;
        }
        
        for (File domainDir : domainDirs) {
            File envFile = new File(domainDir, ENV_FILE);
            if (envFile.exists()) {
                return true; // Found at least one initialized domain
            }
        }
        
        return false;
    }
    
    /**
     * Start interactive domain initialization process
     */
    public boolean startInitialization() {
        try {
            printWelcomeMessage();
            
            if (!confirmInitialization()) {
                printExitMessage();
                return false;
            }
            
            String domainName = getDomainName();
            String basePackage = getBasePackage();
            int architecture = getArchitecture();
            boolean kotlin = getKotlin();
            boolean generateSamples = confirmSampleGeneration();
            
            printProcessingMessage();
            createDomainEnvironment(domainName, basePackage, generateSamples, architecture, kotlin);
            printSuccessMessage(domainName, basePackage, architecture, kotlin);
            
            return true;
            
        } catch (java.util.NoSuchElementException e) {
            // Scanner input not available (non-interactive environment)
            printNonInteractiveMessage();
            return false;
        } catch (Exception e) {
            printErrorMessage(e.getMessage());
            return false;
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
    }
    
    
    /**
     * Alternative non-interactive initialization with default values
     */
    public boolean initializeWithDefaults(String domainName, String basePackage) {
        try {
            // Validate inputs
            if (domainName == null || domainName.trim().isEmpty()) {
                ConsoleLog.printWarn("Domain name cannot be empty");
                return false;
            }
            
            if (basePackage == null || basePackage.trim().isEmpty()) {
                ConsoleLog.printWarn("Base package cannot be empty");
                return false;
            }
            
            domainName = domainName.trim().toLowerCase();
            basePackage = basePackage.trim();
            
            // Validate domain name
            if (!DOMAIN_PATTERN.matcher(domainName).matches()) {
                ConsoleLog.printWarn("Invalid domain name format: " + domainName);
                return false;
            }
            
            // Validate package name
            if (!PACKAGE_PATTERN.matcher(basePackage).matches()) {
                ConsoleLog.printWarn("Invalid package name format: " + basePackage);
                return false;
            }
            
            // Check if domain already exists
            File domainDir = new File(BLUEPRINT_DIR, domainName);
            if (domainDir.exists()) {
                ConsoleLog.printWarn("Domain '" + domainName + "' already exists");
                return false;
            }
            
            ConsoleLog.printInfo("Creating domain: " + domainName + " with package: " + basePackage);
            // Defaults where nobody is present to choose: the layout every
            // existing project already has.
            createDomainEnvironment(domainName, basePackage, true, LAYERED, false);
            ConsoleLog.printInfo("Domain '" + domainName + "' initialized successfully");
            
            return true;
            
        } catch (Exception e) {
            ConsoleLog.printWarn("Failed to initialize domain: " + e.getMessage());
            return false;
        }
    }
    
    private void printWelcomeMessage() {
        System.out.println("\n" + repeatString("=", 70));
        System.out.println("          ElastiCORE Framework - Domain Initialization");
        System.out.println(repeatString("=", 70));
        System.out.println();
        System.out.println("ElastiCORE initialization not found in this project.");
        System.out.println("This wizard will help you create a new domain environment.");
        System.out.println("It is recommended to run with: ./gradlew elcore --console=plain");
        System.out.println();
    }
    
    private boolean confirmInitialization() {
        while (true) {
            System.out.print("Would you like to create a new domain environment? [Y/N]: ");
            String input = scanner.nextLine().trim().toLowerCase();
            
            if (input.equals("y") || input.equals("yes")) {
                return true;
            } else if (input.equals("n") || input.equals("no")) {
                return false;
            } else {
                System.out.println("Please enter Y for yes or N for no.");
            }
        }
    }
    
    private String getDomainName() {
        System.out.println("\n" + repeatString("-", 50));
        System.out.println("Domain Configuration");
        System.out.println(repeatString("-", 50));
        
        while (true) {
            System.out.print("Enter domain name (e.g., crm, custom): ");
            String domain = scanner.nextLine().trim().toLowerCase();
            
            if (domain.isEmpty()) {
                System.out.println("Domain name cannot be empty. Please try again.");
                continue;
            }
            
            if (!DOMAIN_PATTERN.matcher(domain).matches()) {
                System.out.println("Invalid domain name. Use only letters, numbers, and hyphens.");
                System.out.println("Must start with a letter. Please try again.");
                continue;
            }
            
            if (domain.length() > 20) {
                System.out.println("Domain name too long. Maximum 20 characters allowed.");
                continue;
            }
            
            // Check if domain already exists
            File domainDir = new File(BLUEPRINT_DIR, domain);
            if (domainDir.exists()) {
                System.out.println("Domain '" + domain + "' already exists. Please choose a different name.");
                continue;
            }
            
            return domain;
        }
    }
    
    private String getBasePackage() {
        while (true) {
            System.out.print("Enter base package name (e.g., com.elcore.crm): ");
            String packageName = scanner.nextLine().trim();
            
            if (packageName.isEmpty()) {
                System.out.println("Package name cannot be empty. Please try again.");
                continue;
            }
            
            if (!PACKAGE_PATTERN.matcher(packageName).matches()) {
                System.out.println("Invalid package name format. Use Java package naming convention.");
                System.out.println("Example: com.company.domain");
                continue;
            }
            
            return packageName;
        }
    }
    
    private int getArchitecture() {
        System.out.println();
        System.out.println("Architecture - how the generated code is shaped.");
        System.out.println("Every choice reads the same model; they differ in what is derived from it.");
        for (int i = 0; i < ARCHITECTURES.length; i++) {
            System.out.println("  " + (i + 1) + ") " + label(i));
        }
        while (true) {
            System.out.print("Select [1-" + ARCHITECTURES.length + ", default 1]: ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                return LAYERED;
            }
            try {
                int choice = Integer.parseInt(input);
                if (choice >= 1 && choice <= ARCHITECTURES.length) {
                    return choice - 1;
                }
            } catch (NumberFormatException ignored) {
                // fall through to the prompt
            }
            System.out.println("Please enter a number between 1 and " + ARCHITECTURES.length + ".");
        }
    }

    private boolean getKotlin() {
        System.out.println();
        while (true) {
            System.out.print("Language - [J]ava or [K]otlin? [default J]: ");
            String input = scanner.nextLine().trim().toLowerCase();
            if (input.isEmpty() || input.equals("j") || input.equals("java")) {
                return false;
            } else if (input.equals("k") || input.equals("kotlin")) {
                return true;
            }
            System.out.println("Please enter J for Java or K for Kotlin.");
        }
    }

    private boolean confirmSampleGeneration() {
        System.out.println();
        while (true) {
            System.out.print("Generate sample entity and enumeration? [Y/N]: ");
            String input = scanner.nextLine().trim().toLowerCase();
            
            if (input.equals("y") || input.equals("yes")) {
                return true;
            } else if (input.equals("n") || input.equals("no")) {
                return false;
            } else {
                System.out.println("Please enter Y for yes or N for no.");
            }
        }
    }
    
    private void createDomainEnvironment(String domainName, String basePackage, boolean generateSamples,
                                         int architecture, boolean kotlin) throws IOException {
        // Create directory structure
        Path domainPath = Paths.get(BLUEPRINT_DIR, domainName);
        Path modelPath = domainPath.resolve(MODEL_DIR);
        
        Files.createDirectories(modelPath);
        
        // Generate env.yml
        generateEnvFile(domainPath.resolve(ENV_FILE).toFile(), domainName, basePackage, architecture, kotlin);
        
        // Generate sample model.yml if requested
        if (generateSamples) {
            generateSampleModelFile(modelPath.resolve(MODEL_FILE).toFile(), architecture);
        }
    }
    
    private void generateEnvFile(File envFile, String domainName, String basePackage,
                                 int architecture, boolean kotlin) throws IOException {
        String bundle = bundle(architecture, kotlin);
        StringBuilder content = new StringBuilder();
        content.append("# ElastiCORE Domain Configuration\n");
        content.append("# Generated by ElastiCORE Framework\n");
        content.append("# Domain: ").append(domainName).append("\n\n");

        content.append("elasticore:\n");
        content.append("  config:\n");
        content.append("    domainName: ").append(domainName).append("\n");
        content.append("\n");
        content.append("    framework: springboot\n");
        content.append("    mode: jpa\n");
        content.append("    j2ee: jakarta\n");
        content.append("\n");
        content.append("    supportAI: false\n");
        content.append("    testCode: false\n\n");

        content.append("    interface:\n");
        content.append("      pageable: io.elasticore.springboot3.dto.PageableObject\n");
        content.append("      sortable: io.elasticore.springboot3.dto.SortableObject\n\n");

        content.append("    table:\n");
        content.append("      prefix: ").append(domainName).append("_\n\n");

        content.append("    annotations:\n");
        content.append("      #genid: \"genid(").append(basePackage).append(".common.util.IdUtils.newId)\"\n");
        content.append("    template:\n\n");

        // The bundle is written out even when it is the default one, so the
        // choice is visible in the file rather than implied by its absence.
        // Deleting the line changes nothing about what is generated; listing
        // the alternatives beside it is the only reason it is there.
        content.append("    # How the model is read. The commented lines are the other\n");
        content.append("    # bundles - see elasticore-rule-guide.md in the blueprint root.\n");
        if (architecture != LAYERED) {
            // Naming a bundle and saying nothing else would be the worst of
            // the three states: the file would look configured, the build
            // would ignore it, and nothing would report a problem.
            content.append("    #\n");
            content.append("    # These rules are applied by the rule engine, which is a separate\n");
            content.append("    # entry point from the @ElastiCore annotation processor. A project\n");
            content.append("    # on a bundle runs the generation task instead of annotating its\n");
            content.append("    # application class - see the task printed when this domain was\n");
            content.append("    # created, and elasticore-rule-guide.md.\n");
        }
        content.append("    publisher:\n");
        content.append("      rules: [").append(bundle).append("]\n");
        for (int i = 0; i < ARCHITECTURES.length; i++) {
            String alternative = bundle(i, kotlin);
            if (alternative.equals(bundle)) continue;
            content.append("      #rules: [").append(alternative).append("]")
                   .append("   # ").append(label(i)).append("\n");
        }
        content.append("\n");

        content.append("  namespace:\n");
        if (architecture == LAYERED) {
            appendLayeredNamespaces(content, domainName, basePackage);
        } else {
            appendDomainModelNamespaces(content, domainName, basePackage, architecture == HEXAGONAL);
        }

        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(envFile), java.nio.charset.StandardCharsets.UTF_8)) {
            writer.write(content.toString());
        }
    }

    /** The layout every 2.x project has: one package per kind of artifact. */
    private void appendLayeredNamespaces(StringBuilder content, String domainName, String basePackage) {
        String root = basePackage + ".domain." + domainName;
        content.append("    entity: ").append(root).append(".entity\n");
        content.append("    dto: ").append(root).append(".dto\n");
        content.append("    enumeration: ").append(root).append(".enums\n");
        content.append("    repository: ").append(root).append(".repository\n");
        content.append("    service: ").append(root).append(".service\n");
        content.append("    control: ").append(root).append(".controller\n");
        content.append("    port: ").append(root).append(".port\n");
    }

    /**
     * The same artifacts, placed in layers.
     *
     * Layering is configuration rather than a rule - an artifact's package is
     * named per artifact, so nothing here is generated differently. Which is
     * precisely why the wizard has to write these: choosing a domain-model
     * bundle and leaving the 2.x namespaces would put a domain model in flat
     * packages and lose the only part of the choice a reader can see.
     */
    private void appendDomainModelNamespaces(StringBuilder content, String domainName,
                                             String basePackage, boolean hexagonal) {
        String root = basePackage + "." + domainName;
        content.append("    entity: ").append(root).append(".domain.model\n");
        content.append("    enumeration: ").append(root).append(".domain.model\n");
        content.append("    # Spring Data lives in infrastructure. The domain owns an\n");
        content.append("    # interface instead - see domainRepository - so the innermost\n");
        content.append("    # layer names no persistence type.\n");
        content.append("    repository: ").append(root).append(".infrastructure.persistence\n");
        content.append("    domainRepository: ").append(root).append(".domain.repository\n");
        content.append("    persistence: ").append(root).append(".infrastructure.persistence\n");
        content.append("    event: ").append(root).append(".domain.event\n");
        content.append("    domainService: ").append(root).append(".domain.service\n");
        content.append("    service: ").append(root).append(".application\n");
        content.append("    dto: ").append(root).append(".application.dto\n");
        content.append("    control: ").append(root).append(".interfaces.rest\n");
        if (hexagonal) {
            content.append("    # The two directions of the hexagon: an inbound port is what\n");
            content.append("    # the application is entered through, an outbound port is what\n");
            content.append("    # it reaches out with.\n");
            content.append("    inboundPort: ").append(root).append(".application.port.in\n");
        }
        content.append("    port: ").append(root).append(".application.port.out\n");
    }

    /**
     * A first model to edit.
     *
     * The DDD variant is the same two entities with the boundary said out
     * loud. It is worth saying: User and Profile are separate aggregates that
     * refer to each other by id, and a reader who has only seen the layered
     * output has no way to tell that this was a decision rather than the only
     * option. @root on both is what makes the generated repositories and
     * endpoints appear for each of them - and what would remove one of them
     * had @aggregate been written instead.
     */
    private void generateSampleModelFile(File modelFile, int architecture) throws IOException {
        boolean domainModel = architecture != LAYERED;
        StringBuilder content = new StringBuilder();
        content.append("# Sample ElastiCORE Model Definition\n");
        content.append("# Generated by ElastiCORE Framework\n\n");
        
        content.append("entity:\n");
        content.append("  User:\n");
        if (domainModel) {
            content.append("    meta: entity @root @dto @expose(10)")
                   .append(" @invariant(username notEmpty, 'a user is named')\n");
        } else {
            content.append("    meta: entity @dto @expose(10)\n");
        }
        content.append("    fields:\n");
        content.append("      id: long @id @sequence\n");
        content.append("      username: string(50) @search(like)\n");
        content.append("      email: string(100) @search(eq)\n");
        content.append("      status: UserStatus @search(eq)\n");
        content.append("      createdAt: datetime @search(between)\n");
        content.append("      updatedAt: datetime\n\n");
        
        content.append("  Profile:\n");
        if (domainModel) {
            // A separate root, not a member: a profile outlives the request
            // that made it and is edited on its own, so it is its own
            // consistency boundary. Written as @aggregate(User) instead, it
            // would lose its repository and its endpoints and be reachable
            // only through User - which is the choice this line is making.
            content.append("    meta: entity @root @dto\n");
        } else {
            content.append("    meta: entity @dto\n");
        }
        content.append("    fields:\n");
        content.append("      id: long @id @sequence\n");
        content.append("      userId: long @ref(User) @notnull\n");
        content.append("      firstName: string(50)\n");
        content.append("      lastName: string(50)\n");
        content.append("      phoneNumber: string(20)\n");
        content.append("      address: string(255)\n\n");
        
        content.append("enumeration:\n");
        content.append("  UserStatus:\n");
        content.append("    meta: enum @db(code) @json(code)\n");
        content.append("    fields:\n");
        content.append("      code: string(1)\n");
        content.append("      name: string(20)\n");
        content.append("    enum:\n");
        content.append("      ACTIVE: A,Active\n");
        content.append("      INACTIVE: I,Inactive\n");
        content.append("      SUSPENDED: S,Suspended\n");
        
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(modelFile), java.nio.charset.StandardCharsets.UTF_8)) {
            writer.write(content.toString());
        }
    }
    
    private void printProcessingMessage() {
        System.out.println("\n" + repeatString("=", 70));
        System.out.println("Processing domain initialization...");
        System.out.println(repeatString("=", 70));
        System.out.println("* Creating domain directory structure");
        System.out.println("* Generating environment configuration");
        System.out.println("* Setting up namespace mappings");
        System.out.println("* Configuring default annotations");
    }
    
    private void printSuccessMessage(String domainName, String basePackage, int architecture, boolean kotlin) {
        System.out.println("\n" + repeatString("=", 70));
        System.out.println("          ElastiCORE Domain Successfully Initialized!");
        System.out.println(repeatString("=", 70));
        System.out.println("Domain Name: " + domainName);
        System.out.println("Base Package: " + basePackage);
        System.out.println("Architecture: " + label(architecture));
        System.out.println("Language: " + (kotlin ? "Kotlin" : "Java"));
        System.out.println("Rule bundle: " + bundle(architecture, kotlin));
        System.out.println("Location: " + BLUEPRINT_DIR + "/" + domainName + "/");
        System.out.println();
        System.out.println("Next Steps:");
        System.out.println("1. Edit " + BLUEPRINT_DIR + "/" + domainName + "/model/model.yml");
        if (architecture == LAYERED) {
            System.out.println("2. Run './gradlew elcore' again to generate code");
            System.out.println("3. Build your project with './gradlew build'");
        } else {
            printRuleEngineSetup(kotlin, architecture);
        }
        System.out.println();
        System.out.println("To change the architecture later, edit publisher.rules in env.yml -");
        System.out.println("the alternatives are listed there, commented out.");
        System.out.println();
        //System.out.println("For documentation and examples, visit:");
        //System.out.println("https://github.com/elasticore/elasticore-framework");
        System.out.println(repeatString("=", 70));
    }
    
    /**
     * How a project reaches the rule engine: the plugin.
     *
     * This used to print a hand-written JavaExec task, which was the approach
     * the plugin had just replaced - the wizard teaching every new user the
     * thing the same release deprecated. An audit caught it. The plugin also
     * settles the two traps the old text had to warn about in prose: it wires
     * compileJava to the right generator, and it derives the language switch
     * from the declaration so a bundle and a switch cannot disagree.
     */
    private void printRuleEngineSetup(boolean kotlin, int architecture) {
        String architectureWord = ARCHITECTURES[architecture] == RulesConfig.Architecture.HEXAGONAL
                ? "hexagonal" : "ddd";
        System.out.println("2. Apply the ElastiCORE plugin in build.gradle and declare the choice");
        System.out.println("   you just made - the rule engine is a separate entry point, and a");
        System.out.println("   bundle does nothing until something runs it:");
        System.out.println();
        System.out.println("     plugins {");
        System.out.println("         id 'io.elasticore' version '<elasticore-version>'");
        System.out.println("     }");
        System.out.println();
        System.out.println("     elasticore {");
        System.out.println("         architecture = '" + architectureWord + "'");
        System.out.println("         language     = '" + (kotlin ? "kotlin" : "java") + "'");
        System.out.println("     }");
        System.out.println();
        System.out.println("   (pluginManagement in settings.gradle must name your ElastiCORE");
        System.out.println("   repository - see elasticore-rule-guide.md, 'Gradle plugin'.)");
        System.out.println("3. Do NOT annotate your application class with @ElastiCore. The two");
        System.out.println("   paths generate the same files from different rules; a project on a");
        System.out.println("   bundle uses the plugin's task alone.");
        System.out.println("4. Run './gradlew elcoreGen', then './gradlew build'");
    }

    private void printExitMessage() {
        System.out.println("\nElastiCORE initialization cancelled.");
        System.out.println("You can run './gradlew elcore' again anytime to initialize.");
    }
    
    private void printNonInteractiveMessage() {
        System.out.println("\n" + repeatString("=", 70));
        System.out.println("          ElastiCORE Framework - Non-Interactive Mode");
        System.out.println(repeatString("=", 70));
        System.out.println("Cannot run interactive initialization in current environment.");
        System.out.println("This typically happens when running through Gradle builds.");
        System.out.println();
        System.out.println("To initialize ElastiCORE manually:");
        System.out.println("1. Create directory: src/main/resources/blueprint/{domain-name}/");
        System.out.println("2. Create env.yml configuration file");
        System.out.println("3. Create model/ directory with model.yml");
        System.out.println();
        System.out.println("Or use programmatic initialization in your build script.");
        System.out.println(repeatString("=", 70));
    }
    
    private void printErrorMessage(String error) {
        System.out.println("\n" + repeatString("!", 70));
        System.out.println("ERROR: Domain initialization failed");
        System.out.println(repeatString("!", 70));
        System.out.println("Reason: " + error);
        System.out.println();
        System.out.println("Please check your permissions and try again.");
        System.out.println("If the problem persists, contact support.");
        System.out.println(repeatString("!", 70));
    }
    
    /**
     * Utility method to repeat a string (Java 8 compatible)
     */
    private static String repeatString(String str, int count) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) {
            result.append(str);
        }
        return result.toString();
    }
    
    /**
     * Main method for standalone initialization
     * Usage: java -cp <classpath> io.elasticore.base.util.InitializationHelper [domain-name] [base-package]
     */
    public static void main(String[] args) {
        InitializationHelper helper = new InitializationHelper();
        
        if (isInitialized()) {
            System.out.println("ElastiCORE environment already initialized.");
            System.exit(0);
        }
        
        // Check for command line arguments
        if (args.length >= 2) {
            String domainName = args[0];
            String basePackage = args[1];
            System.out.println("Initializing with provided arguments...");
            System.out.println("Domain: " + domainName);
            System.out.println("Package: " + basePackage);
            
            boolean success = helper.initializeWithDefaults(domainName, basePackage);
            System.exit(success ? 0 : 1);
        } else {
            // Interactive mode
            boolean success = helper.startInitialization();
            System.exit(success ? 0 : 1);
        }
    }
}