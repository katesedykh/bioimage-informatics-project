package ch.epfl.bii.ij2command;

import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

import ij.*;
import ij.process.*;
import ij.io.*;
import ij.plugin.*;
import ij.measure.*;
import ij.plugin.frame.*;
import ij.gui.Plot;
import ij.gui.Roi;
import ij.plugin.filter.ParticleAnalyzer;
import ij.measure.ResultsTable;
import ij.measure.Measurements;



import inra.ijpb.morphology.Morphology;
import inra.ijpb.morphology.Strel;

@Plugin(type = Command.class, menuPath = "Plugins>BII 2023>Spindle Analysis")
public class Spindle_Analysis_Plugin implements Command {

    public void run() {
        OpenDialog od = new OpenDialog("Choose an Image File", "");
        String fileName = od.getFileName();
        String directory = od.getDirectory();
        ImagePlus imp = IJ.openImage(directory + fileName);
        
        int frames = imp.getNFrames();
        int Fluorescence_channel = 1;
        int DIC_channel = 2;

        // Split channels
        ImagePlus[] channels = ChannelSplitter.split(imp);
        
        // Processing for DIC Channel
        ImagePlus DIC = channels[DIC_channel-1];
        IJ.run(DIC, "32-bit", "");
        IJ.run(DIC, "Variance...", "radius=3 stack");
        IJ.run(DIC, "Enhance Contrast", "saturated=0.35");
        IJ.run(DIC, "Enhance Contrast", "saturated=0.35");
        //IJ.run(DIC, "Close", "");
        IJ.run(DIC, "8-bit", "");
        IJ.run(DIC, "Gray Morphology", "radius=13 type=circle operator=close");
        IJ.setAutoThreshold(DIC, "Default dark no-reset");
        IJ.setAutoThreshold(DIC, "Li dark no-reset");
        IJ.run(DIC, "Convert to Mask", "method=Li background=Light calculate black");
        
        //IJ.run(DIC, "Mean...", "radius=3 stack");
        //IJ.run(DIC, "Subtract Background...", "rolling=40 stack");
        //IJ.setAutoThreshold(DIC, "Li dark stack");
        DIC.show();

        // Processing for Fluorescence Channel
        ImagePlus Fluorescence = channels[Fluorescence_channel-1];
        IJ.run(Fluorescence, "Subtract Background...", "rolling=50 stack");

        // Analyse particles
        //IJ.run(DIC, "Analyze Particles...", "size=20-Infinity circularity=0.5-1.00 show=[Overlay Outlines] display exclude stack");

        // Get the results
        ResultsTable rt = new ResultsTable();        
        int options = ParticleAnalyzer.ADD_TO_MANAGER;
        int measurements = Measurements.ALL_STATS;
        RoiManager roiManager = new RoiManager(true);
        
        // Create an array to store condensation index
        double[] condensationIndex = new double[roiManager.getCount()];

        for (int slice = 1; slice <= DIC.getNSlices(); slice++) {
            DIC.setSlice(slice);
            //ResultsTable rt = new ResultsTable();
            ParticleAnalyzer analyzer = new ParticleAnalyzer(options, measurements, rt, 20, Double.POSITIVE_INFINITY, 0.5, 1.0);
            analyzer.analyze(DIC);
            for (int i = 0; i < roiManager.getCount(); i++) {
                // Set the current slice
                Fluorescence.setSlice(slice);

                // Set the ROI to the Fluorescence channel
                Roi roi = roiManager.getRoi(i);
                Fluorescence.setRoi(roi);

                // Calculate condensation index based on Skewness
                ImageStatistics stats = Fluorescence.getStatistics(Measurements.SKEWNESS);
                condensationIndex[slice - 1] = stats.skewness;
            }
        }
            
           

        // Create an array to represent frames
        double[] framesArray = new double[frames];
        for (int i = 0; i < frames; i++) {
            framesArray[i] = i;
        }

        // Create a new plot
        Plot plot = new Plot("Condensation Index", "Frame", "Index");
        plot.add("line", framesArray, condensationIndex);
        plot.show();

    }
}
