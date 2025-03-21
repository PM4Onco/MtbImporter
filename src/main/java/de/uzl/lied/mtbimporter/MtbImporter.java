package de.uzl.lied.mtbimporter;

import de.uzl.lied.mtbimporter.jobs.CheckDropzone;
import de.uzl.lied.mtbimporter.jobs.FhirResolver;
import de.uzl.lied.mtbimporter.jobs.StudyHandler;
import de.uzl.lied.mtbimporter.jobs.mdr.centraxx.CxxMdrLogin;
import de.uzl.lied.mtbimporter.jobs.mdr.dataelementhub.DataElementHubLogin;
import de.uzl.lied.mtbimporter.model.CbioPortalStudy;
import de.uzl.lied.mtbimporter.settings.ConfigurationLoader;
import de.uzl.lied.mtbimporter.settings.Mdr;
import de.uzl.lied.mtbimporter.settings.Settings;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import org.tinylog.Logger;

/**
 * Fully automated data import tool for cBioPortal
 * (https://github.com/cbioportal/cbioportal). Best deployed with
 * MIRACUM-cBioPortal (https://github.com/PM4Onco/MTB-cbioportal) and
 * MIRACUM-Pipe (https://github.com/AG-Boerries/MIRACUM-Pipe-docker).
 */
public final class MtbImporter {

    private MtbImporter() {
    }

    /**
     * Entry point method for MtbImporter.
     * @param args The arguments of the program.
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        InputStream settingsYaml = ClassLoader.getSystemClassLoader().getResourceAsStream("settings.yaml");
        if (args.length == 1) {
            settingsYaml = new FileInputStream(args[0]);
        }

        ConfigurationLoader configLoader = new ConfigurationLoader();
        configLoader.loadConfiguration(settingsYaml, Settings.class);

        FhirResolver.initalize();
        for (Mdr m : Settings.getMdr()) {
            if (m.getCxx() != null) {
                CxxMdrLogin.login(m.getCxx());
            }
            if (m.getDataelementhub() != null) {
                DataElementHubLogin.login(m.getDataelementhub());
            }
        }

        CbioPortalStudy study = StudyHandler.load(Settings.getMainStudyId());

        Timer t = new Timer();
        CheckDropzone checkDropzone = new CheckDropzone(study);
        t.schedule(checkDropzone, 0, Settings.getCronIntervall());

        Logger.info("MtbImporter started!");
    }
}
