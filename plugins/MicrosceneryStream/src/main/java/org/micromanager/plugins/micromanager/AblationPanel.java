package org.micromanager.plugins.micromanager;

import fromScenery.Settings;
import ij.ImagePlus;
import ij.gui.PointRoi;
import ij.gui.Roi;
import microscenery.Ablation;
import microscenery.Util;
import microscenery.hardware.micromanagerConnection.MMCoreConnector;
import microscenery.hardware.micromanagerConnection.MicromanagerWrapper;
import microscenery.signals.AblationResults;
import net.miginfocom.swing.MigLayout;
import org.joml.Vector3f;
import org.micromanager.Studio;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AblationPanel extends JPanel {
    private final MMCoreConnector mmCon;
    private final Studio studio;
    private final Settings msSettings;

    private List<Vector3f> plannedCut = null;

    private final JLabel totalTimeLabel = new JLabel("no data");
    private final JLabel meanTimeLabel = new JLabel("no data");
    private final JLabel stdTimeLabel = new JLabel("no data");

    public AblationPanel(MMCoreConnector mmCon, Studio studio, MicromanagerWrapper mmWrapper) {
        this.mmCon = mmCon;
        this.studio = studio;

        msSettings = Util.getMicroscenerySettings();

        Ablation.initAblationSettings();
        msSettings.set("Ablation.flipX", false);
        msSettings.set("Ablation.flipY", false);
        msSettings.set("Ablation.laserOffsetXpx", 0);
        msSettings.set("Ablation.laserOffsetYpx", 0);

        this.setLayout(new MigLayout());

        TitledBorder title;
        title = BorderFactory.createTitledBorder("Photomanipulation");
        this.setBorder(title);

        this.add(new JLabel("Shutter:"));
        JComboBox<String> shutterComboBox = new JComboBox<>( studio.shutter().getShutterDevices().toArray(new String[0]));
        shutterComboBox.addActionListener(e -> {
            @SuppressWarnings("unchecked") JComboBox<String> cb = (JComboBox<String>)e.getSource();
            String name = (String)cb.getSelectedItem();
            assert name != null;
            msSettings.set("Ablation.Shutter",name);
        });
        this.add(shutterComboBox, "wrap");

        this.add(new JLabel("Total Time(ms):"));
        this.add(totalTimeLabel,"wrap");
        this.add(new JLabel("mean time per point(ms):"));
        this.add(meanTimeLabel,"wrap");
        this.add(new JLabel("mtpp std(ms):"));
        this.add(stdTimeLabel,"wrap");

        JButton calculateLaserOffsetButton = new JButton("Calculate laser offset");
        calculateLaserOffsetButton.addActionListener(e -> calculateLaserOffset());
        this.add(calculateLaserOffsetButton, "wrap");

        JButton planButton = new JButton("Plan");
        planButton.addActionListener(e -> planAblation());
        this.add(planButton, "");

        JButton executeBut = new JButton("execute");
        executeBut.addActionListener(e ->{
            if (plannedCut == null) {
                this.studio.alerts().postAlert("Missing ablation plan", null, "Please plan a ablation path first");
                return;
            }
            Ablation.executeAblationCommandSequence(
                    mmWrapper,
                    Ablation.buildLaserPath(plannedCut)
            );
        });
        this.add(executeBut, "wrap");
    }

    @SuppressWarnings("deprecation")
    private void calculateLaserOffset(){
        ImagePlus img;
        try {
            img = this.studio.getSnapLiveManager().getDisplay().getImagePlus();
        } catch (NullPointerException e){
            this.studio.alerts().postAlert("Could not grab image", null, "Image required for operation.");
            return;
        }
        //IJ.getImage()
        // do calibration like https://imagej.nih.gov/ij/developer/source/ij/plugin/Coordinates.java.html ?

        if (img == null) {
            this.studio.alerts().postAlert("Calculating laser offset not possible", null, "Image required.");
            return;
        }

        Roi roi = img.getRoi();
        if (!(roi instanceof PointRoi)) {
            this.studio.alerts().postAlert("Calculating laser offset  not possible", null, "Point selection required.");
            return;
        }
        PointRoi pRoi = (PointRoi) roi;
        if (pRoi.getNCoordinates() != 1){
            this.studio.alerts().postAlert("Calculating laser offset  not possible", null, "Single point selection required.");
            return;
        }
        int imgMidX = img.getWidth() / 2;
        int imgMidY = img.getHeight() / 2;

        Point point = pRoi.getContainedPoints()[0];

        msSettings.set("Ablation.laserOffsetXpx", point.x - imgMidX);
        msSettings.set("Ablation.laserOffsetYpx", point.y - imgMidY);
    }

    @SuppressWarnings("deprecation")
    private void planAblation() {
        ImagePlus img;
        try {
            img = this.studio.getSnapLiveManager().getDisplay().getImagePlus();
        } catch (NullPointerException e){
            this.studio.alerts().postAlert("Could not grab image", null, "Image required for operation.");
            return;
        }
        //IJ.getImage()
        // do calibration like https://imagej.nih.gov/ij/developer/source/ij/plugin/Coordinates.java.html ?

        if (img == null) {
            this.studio.alerts().postAlert("Ablation not possible", null, "Image required.");
            return;
        }

        Roi roi = img.getRoi();
        if (roi == null) {
            this.studio.alerts().postAlert("Ablation not possible", null, "Selection required.");
            return;
        }

        Polygon polygon = roi.getPolygon();
        int[] xa = polygon.xpoints;
        int[] ya = polygon.ypoints;
        double pixelSize = this.studio.core().getPixelSizeUm();

        Vector3f ablationPrecisionSetting = Util.getVector3(msSettings, "Ablation.precision", new Vector3f(1f));
        Vector3f precision = ablationPrecisionSetting.div((float) pixelSize);

        // points to sample in image space
        List<Vector3f> samplePointsIS = new ArrayList<>();
        Vector3f prev = null;
        for(int i = 0; i < polygon.npoints; i++){
            double x = xa[i];
            double y = ya[i];

            Vector3f cur = new Vector3f((float)x, (float) y,0);
            if(prev != null){
                List<Vector3f> sampled = Ablation.sampleLineGrid(prev, cur, precision);
                samplePointsIS.addAll(sampled);
            }
            samplePointsIS.add(cur);
            prev = cur;
        }
        if (samplePointsIS.size() > 1){
            samplePointsIS.addAll(Ablation.sampleLineGrid(prev, samplePointsIS.get(0),precision));
        }

        int[] newX = samplePointsIS.stream().mapToInt(v -> (int) v.x).toArray();
        int[] newY = samplePointsIS.stream().mapToInt(v -> (int) v.y).toArray();


        PointRoi newPoly = new PointRoi(newX, newY, newX.length);
        img.setOverlay(newPoly, Color.YELLOW, 3,null);

        // -- transform to stage space --
        int imgMidX = img.getWidth() / 2;
        int imgMidY = img.getHeight() / 2;
        Vector3f stagePos = this.mmCon.getStagePosition();

        plannedCut = samplePointsIS.stream().peek(vec -> {
            // assuming the laser points to the middle of the image and center of stage
            // move path "center from middle of image to origin
            vec.x -= imgMidX;
            vec.y -= imgMidY;

            vec.x += msSettings.get("Ablation.laserOffsetXpx", 0);
            vec.y += msSettings.get("Ablation.laserOffsetYpx", 0);

            if (msSettings.get("Ablation.flipX", false)){
                vec.x = vec.x *-1;
            }
            if (msSettings.get("Ablation.flipY", false)){
                vec.y = vec.y *-1;
            }
            // transform to stage space
            // z is 0 therefore mul is ok
            vec.mul((float) pixelSize);
            // move origin relative to current stage position (should be the same as position of the image)
            vec.add(stagePos);
        }).collect(Collectors.toList());
    }

    public void updateTimings(AblationResults results){
        totalTimeLabel.setText(results.getTotalTimeMillis()+"");
        meanTimeLabel.setText(results.mean() + "");
        double[] dar = results.getPerPointTime().stream().mapToDouble(Integer::doubleValue).toArray();
        if (dar.length > 2){
            stdTimeLabel.setText(calculateStandardDeviation(dar).intValue()+"");
        }
    }

    // taken from https://www.baeldung.com/java-calculate-standard-deviation
    public static Double calculateStandardDeviation(double[] array) {

        // get the sum of array
        double sum = 0.0;
        for (double i : array) {
            sum += i;
        }

        // get the mean of array
        int length = array.length;
        double mean = sum / length;

        // calculate the standard deviation
        double standardDeviation = 0.0;
        for (double num : array) {
            standardDeviation += Math.pow(num - mean, 2);
        }

        return Math.sqrt(standardDeviation / length);
    }
}
