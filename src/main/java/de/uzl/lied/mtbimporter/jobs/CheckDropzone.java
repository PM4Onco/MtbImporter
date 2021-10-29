package de.uzl.lied.mtbimporter.jobs;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TimerTask;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import de.samply.common.mdrclient.MdrConnectionException;
import de.samply.common.mdrclient.MdrInvalidResponseException;
import de.uzl.lied.mtbimporter.model.CbioPortalStudy;
import de.uzl.lied.mtbimporter.model.ClinicalPatient;
import de.uzl.lied.mtbimporter.settings.InputFolder;
import de.uzl.lied.mtbimporter.settings.Settings;
import de.uzl.lied.mtbimporter.tasks.AddGeneticData;
import de.uzl.lied.mtbimporter.tasks.AddMetaData;
import de.uzl.lied.mtbimporter.tasks.AddHisData;
import de.uzl.lied.mtbimporter.tasks.AddResourceData;
import de.uzl.lied.mtbimporter.tasks.AddSignatureData;
import de.uzl.lied.mtbimporter.tasks.ImportStudy;

public class CheckDropzone extends TimerTask {

    CbioPortalStudy study;

    public CheckDropzone(CbioPortalStudy study) {
        this.study = study;
    }

    @Override
    public void run() {

        Long newState = System.currentTimeMillis();
        int count = 0;
        CbioPortalStudy newStudy = new CbioPortalStudy();

        System.out.println("Checking for files!");
        for (InputFolder inputfolder : Settings.getInputFolders()) {
            File[] files = new File(inputfolder.getSource()).listFiles();

            if (files.length == 0 || (files.length == 1 && files[0].getName().equals(".gitkeep"))) {
                System.out.println("No new files at " + inputfolder.getSource() + ".");
                continue;
            } else {
                count++;
            }

            for (File f : files) {
                if (f.getName().contains("Diagnosen_Vorst")) {
                    try {
                        AddHisData.prepare(f, newStudy);
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
            for (File f : files) {
                if(f.getName().equals(".gitkeep")) {
                    continue;
                }
                System.out.println("Found " + f.getAbsolutePath());
                try {
                    switch (FilenameUtils.getExtension(f.getName())) {
                    case "maf":
                        AddGeneticData.processMafFile(newStudy, f);
                        break;
                    case "txt":
                        switch (determineDatatype(f)) {
                        case "cna":
                            AddGeneticData.processCnaFile(newStudy, f);
                            break;
                        case "mutsigLimit":
                            AddSignatureData.processLimit(newStudy, f);
                            break;
                        case "mutsigContribution":
                            AddSignatureData.processContribution(newStudy, f);
                            break;
                        }
                        break;
                    case "seg":
                        AddGeneticData.processSegFile(newStudy, f);
                        break;
                    case "pdf":
                        AddResourceData.processPdfFile(newStudy, f);
                        break;
                    case "RData":
                        AddMetaData.processRData(newStudy, f);
                        break;
                    case "csv":
                        AddHisData.processCsv(newStudy, f);
                        break;
                    }

                    if (inputfolder.getTarget() == null || inputfolder.getTarget().length() == 0) {
                        f.delete();
                    } else {
                        new File(inputfolder.getTarget() + "/" + newState).mkdirs();
                        Files.move(f.toPath(),
                                new File(inputfolder.getTarget() + "/" + newState + "/" + f.getName()).toPath(),
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                    System.out.println("Processed file " + f.getAbsolutePath());
                } catch (IOException | ExecutionException | MdrConnectionException | MdrInvalidResponseException e) {
                    System.err.println("Could not process file " + f.getAbsolutePath());
                }
            }
        }

        if (count > 0) {
            try {
                FileUtils.copyDirectory(new File(Settings.getStudyFolder() + study.getStudyId() + "/" + study.getState()),
                        new File(Settings.getStudyFolder() + study.getStudyId() + "/" + newState));

                StudyHandler.merge(study, newStudy);
                StudyHandler.write(study, newState);
                ImportStudy.importStudy(study.getStudyId(), newState, Settings.getOverrideWarnings());
                study.setState(newState);

                Map<String, List<String>> patientsByDate = new HashMap<String, List<String>>();
                Map<String, CbioPortalStudy> patientStudy = new HashMap<String, CbioPortalStudy>();
                for (ClinicalPatient patient : newStudy.getPatients()) {
                    if (patient.getAdditionalAttributes().containsKey("PRESENTATION_DATE")) {
                        List<String> al = patientsByDate.getOrDefault(
                                (String) patient.getAdditionalAttributes().get("PRESENTATION_DATE"),
                                new ArrayList<String>());
                        al.add(patient.getPatientId());
                        patientsByDate.put((String) patient.getAdditionalAttributes().get("PRESENTATION_DATE"), al);
                        patientStudy.put(patient.getPatientId(), StudyHandler.getPatientStudy(study, patient.getPatientId()));
                    }
                }
                for(Entry<String, List<String>> e : patientsByDate.entrySet()) {
                    CbioPortalStudy s = StudyHandler.load(study.getStudyId() + "_" + e.getKey());
                    s.getMetaFile("meta_study.txt").setAdditionalAttributes("name", e.getKey() + " " + s.getMetaFile("meta_study.txt").getAdditionalAttributes().get("name"));
                    s.getMetaFile("meta_study.txt").setAdditionalAttributes("short_name", e.getKey() + " " + s.getMetaFile("meta_study.txt").getAdditionalAttributes().get("short_name"));
                    s.getMetaFile("meta_study.txt").setAdditionalAttributes("description", e.getKey() + " " + s.getMetaFile("meta_study.txt").getAdditionalAttributes().get("description"));
                    for(String patient : e.getValue()) {
                        StudyHandler.merge(s, patientStudy.get(patient));
                    }
                    StudyHandler.write(s, s.getState());
                    ImportStudy.importStudy(s.getStudyId(), s.getState(), Settings.getOverrideWarnings());
                }
            } catch (IOException | InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    private String determineDatatype(File f) throws FileNotFoundException {

        Scanner scanner = new Scanner(f);
        String header = "";
        String content = "";
        if (scanner.hasNextLine()) {
            header = scanner.nextLine();
        }
        if (scanner.hasNextLine()) {
            content = scanner.nextLine();
        }
        scanner.close();
        if (header.contains("Entrez_Gene_Id") && header.contains("Hugo_Symbol")) {
            return "cna";
        }
        if (header.contains("ENTITY_STABLE_ID") && header.contains("NAME") && header.contains("DESCRIPTION")) {
            if (content.contains("contribution")) {
                return "mutsigContribution";
            }
            if (content.contains("limit")) {
                return "mutsigLimit";
            }
        }

        return "";

    }

}
