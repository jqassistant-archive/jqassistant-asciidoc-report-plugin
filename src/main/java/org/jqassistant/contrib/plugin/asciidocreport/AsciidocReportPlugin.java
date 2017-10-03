package org.jqassistant.contrib.plugin.asciidocreport;

import static java.util.Collections.singletonList;

import java.io.File;
import java.io.FilenameFilter;
import java.util.*;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.OptionsBuilder;
import org.asciidoctor.SafeMode;
import org.asciidoctor.extension.JavaExtensionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.buschmais.jqassistant.core.analysis.api.Result;
import com.buschmais.jqassistant.core.analysis.api.rule.*;
import com.buschmais.jqassistant.core.report.api.ReportException;
import com.buschmais.jqassistant.core.report.api.ReportHelper;
import com.buschmais.jqassistant.core.report.api.ReportPlugin;
import com.buschmais.jqassistant.plugin.common.api.scanner.filesystem.FilePatternMatcher;

public class AsciidocReportPlugin implements ReportPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsciidocReportPlugin.class);

    private static final String PROPERTY_DIRECTORY = "asciidoc.report.directory";
    private static final String PROPERTY_RULE_DIRECTORY = "asciidoc.report.rule.directory";
    private static final String PROPERTY_FILE_INCLUDE = "asciidoc.report.file.include";
    private static final String PROPERTY_FILE_EXCLUDE = "asciidoc.report.file.exclude";

    private static final String DEFAULT_DIRECTORY = "jqassistant/report/asciidoc";
    private static final String DEFAULT_RULE_DIRECTORY = "jqassistant/report/asciidoc";
    public static final String BACKEND_HTML5 = "html5";

    private File reportDirectory;

    private File ruleDirectory;

    private String fileInclude;

    private String fileExclude;

    private Map<String, RuleResult> conceptResults;
    private Map<String, RuleResult> constraintResults;

    @Override
    public void initialize() throws ReportException {
    }

    @Override
    public void configure(Map<String, Object> properties) throws ReportException {
        this.reportDirectory = getFile(PROPERTY_DIRECTORY, DEFAULT_DIRECTORY, properties);
        this.ruleDirectory = getFile(PROPERTY_RULE_DIRECTORY, DEFAULT_RULE_DIRECTORY, properties);
        if (this.reportDirectory.mkdirs()) {
            LOGGER.info("Created directory '" + this.reportDirectory.getAbsolutePath() + "'.");
        }
        this.fileInclude = (String) properties.get(PROPERTY_FILE_INCLUDE);
        this.fileExclude = (String) properties.get(PROPERTY_FILE_EXCLUDE);
    }

    private File getFile(String property, String defaultValue, Map<String, Object> properties) {
        String directoryName = (String) properties.get(property);
        return directoryName != null ? new File(directoryName) : new File(defaultValue);
    }

    @Override
    public void begin() throws ReportException {
        conceptResults = new HashMap<>();
        constraintResults = new HashMap<>();
    }

    @Override
    public void end() throws ReportException {
        if (ruleDirectory.exists()) {
            Asciidoctor asciidoctor = Asciidoctor.Factory.create();
            JavaExtensionRegistry extensionRegistry = asciidoctor.javaExtensionRegistry();
            OptionsBuilder optionsBuilder = OptionsBuilder.options().mkDirs(true).baseDir(ruleDirectory).toDir(reportDirectory).backend(BACKEND_HTML5)
                    .safe(SafeMode.UNSAFE).option("source-highlighter", "coderay");
            final FilePatternMatcher filePatternMatcher = FilePatternMatcher.Builder.newInstance().include(this.fileInclude).exclude(this.fileExclude).build();
            File[] files = ruleDirectory.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return filePatternMatcher.accepts(name);
                }
            });
            for (File file : files) {
                LOGGER.info("Rendering " + file.getPath());
                extensionRegistry.treeprocessor(new ResultTreePreprocessor(conceptResults, constraintResults));
                asciidoctor.convertFile(file, optionsBuilder);
            }
        }
    }

    @Override
    public void beginConcept(Concept concept) throws ReportException {
    }

    @Override
    public void endConcept() throws ReportException {
    }

    @Override
    public void beginGroup(Group group) throws ReportException {
    }

    @Override
    public void endGroup() throws ReportException {
    }

    @Override
    public void beginConstraint(Constraint constraint) throws ReportException {
    }

    @Override
    public void endConstraint() throws ReportException {
    }

    @Override
    public void setResult(Result<? extends ExecutableRule> result) throws ReportException {
        ExecutableRule rule = result.getRule();
        Report report = rule.getReport();
        if (isAsciidocReport(report)) {
            if (rule instanceof Concept) {
                this.conceptResults.put(rule.getId(), getRuleResult(result));
            } else if (rule instanceof Constraint) {
                this.constraintResults.put(rule.getId(), getRuleResult(result));
            }
        }
    }

    private RuleResult getRuleResult(Result<? extends ExecutableRule> result) {
        RuleResult.RuleResultBuilder ruleResultBuilder = RuleResult.builder();
        List<String> columnNames = result.getColumnNames();
        ruleResultBuilder.rule(result.getRule()).severity(result.getRule().getSeverity().getInfo(result.getSeverity())).status(result.getStatus())
                .columnNames(columnNames != null ? columnNames : singletonList("No Result"));
        for (Map<String, Object> row : result.getRows()) {
            Map<String, String> resultRow = new LinkedHashMap<>();
            for (Map.Entry<String, Object> rowEntry : row.entrySet()) {
                resultRow.put(rowEntry.getKey(), ReportHelper.getLabel(rowEntry.getValue()));
            }
            ruleResultBuilder.row(resultRow);
        }
        return ruleResultBuilder.build();
    }

    /**
     * Verify if this report shall be executed.
     *
     * FIXME This logic should be provided by the framework.
     *
     * @param report
     *            The report configured for the executed rule.
     * @return <code>true</code> if this report is selected.
     */
    private boolean isAsciidocReport(Report report) {
        Set<String> selectedTypes = report.getSelectedTypes();
        return selectedTypes == null || selectedTypes.isEmpty();
    }

}