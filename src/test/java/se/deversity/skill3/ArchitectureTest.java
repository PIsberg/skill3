package se.deversity.skill3;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Enforces Skill3's layering so the pipeline stays acyclic and the domain stays pure:
 * {@code model} is a leaf, {@code cli} is the composition root only the entry point may touch.
 */
@AnalyzeClasses(packages = "se.deversity.skill3",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** The domain model must not reach up into any pipeline/CLI/IO layer. */
    @ArchTest
    static final ArchRule modelDependsOnNothingInternal =
            noClasses().that().resideInAPackage("..model..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..cli..", "..pipeline..", "..llm..", "..skillspector..", "..web..");

    /** Only the {@link Skill3App} composition root wires the CLI; lower layers stay CLI-free. */
    @ArchTest
    static final ArchRule lowerLayersDoNotDependOnCli =
            noClasses().that()
                    .resideInAnyPackage("..pipeline..", "..llm..", "..model..", "..skillspector..", "..web..")
                    .should().dependOnClassesThat().resideInAPackage("..cli..");

    /** No cyclic dependencies between the top-level sub-packages. */
    @ArchTest
    static final ArchRule packagesAreFreeOfCycles =
            slices().matching("se.deversity.skill3.(*)..").should().beFreeOfCycles();
}
