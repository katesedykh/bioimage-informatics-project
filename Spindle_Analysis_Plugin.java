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
import ij.plugin.filter.RankFilters;
import ij.measure.ResultsTable;
import ij.measure.Measurements;



import inra.ijpb.morphology.Morphology;
import inra.ijpb.morphology.Strel;
import net.imagej.ops.Ops.Filter;
import net.imagej.ops.Ops.Filter.Variance;

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
        //normalizeStack(DIC);
        IJ.run(DIC, "32-bit", "");
        IJ.run(DIC, "Variance...", "radius=3 stack"); // changed from 3
        IJ.run(DIC, "Enhance Contrast", "saturated=0.35");
        IJ.run(DIC, "Enhance Contrast", "saturated=0.35");
        //IJ.run(DIC, "Close", "");
        IJ.run(DIC, "8-bit", "");
        IJ.run(DIC, "Gray Morphology", "radius=13 type=circle operator=close");
        IJ.run(DIC, "Mean...", "radius=3");
        
        //IJ.setAutoThreshold(DIC, "Default dark no-reset");
        IJ.setAutoThreshold(DIC, "Li no-reset");
        DIC.show();
        //IJ.run("Make Binary", "method=Li background=Light calculate black");
        IJ.run(DIC, "Convert to Mask", "method=Li background=Light calculate black");
        
        //IJ.run(DIC, "Mean...", "radius=3 stack");
        //IJ.run(DIC, "Subtract Background...", "rolling=40 stack");
        //IJ.setAutoThreshold(DIC, "Li dark stack");
        DIC.show();

        // Processing for Fluorescence Channel
        ImagePlus Fluorescence = channels[Fluorescence_channel-1];
        normalizeStack(Fluorescence);
        IJ.run(Fluorescence, "Subtract Background...", "rolling=50 stack");
        

        // Analyse particles
        //IJ.run(DIC, "Analyze Particles...", "size=20-Infinity circularity=0.5-1.00 show=[Overlay Outlines] display exclude stack");
        IJ.run("Analyze Particles...", "size=25-Infinity circularity=0.50-1.00 show=Overlay display exclude overlay add stack");
        // Get the results
        RoiManager roiManager = RoiManager.getRoiManager();
        ResultsTable rt = ResultsTable.getResultsTable();/*
        int options = ParticleAnalyzer.ADD_TO_MANAGER | ParticleAnalyzer.DOES_STACKS | ParticleAnalyzer.PARALLELIZE_STACKS |
        		ParticleAnalyzer.SHOW_OUTLINES;
        int measurements = Measurements.ALL_STATS;
        RoiManager roiManager = new RoiManager(true);
        ParticleAnalyzer analyzer = new ParticleAnalyzer(options, measurements, rt, 20, Double.POSITIVE_INFINITY, 0.5, 1.0);
        
        analyzer.analyze(DIC);*/

        // Create an array to store condensation index
        double[] condensationIndex = new double[roiManager.getCount()];

        // Apply each ROI from the RoiManager to the Fluorescence image and measure the intensity
        for (int i = 0; i < roiManager.getCount(); i++) {
            // Set the ROI to the Fluorescence channel
            Roi roi = roiManager.getRoi(i);
            Fluorescence.setRoi(roi);

            // Calculate condensation index based on skewness
            ImageStatistics stats = Fluorescence.getStatistics(Measurements.SKEWNESS);
            condensationIndex[i] = stats.skewness; 
        }


        // Create an array to represent frames
        double[] framesArray = new double[frames];
        for (int i = 0; i < frames; i++) {
            framesArray[i] = i;
        }
        
        smoothArray(condensationIndex, 2);
        
        // Create a new plot
        Plot plot = new Plot("Condensation Index", "Frame", "Index");
        plot.add("line", framesArray, condensationIndex);
        
        plot.show();
        plot.setLimits(1, frames, plot.getLimits()[2], plot.getLimits()[3]);
        plot.update();

    }
    
    private void normalizeStack(ImagePlus imp) {
		ContrastEnhancer ce = new ContrastEnhancer();
		ce.setNormalize(true);
		ce.setProcessStack(true);
		ce.setUseStackHistogram(true);
		ce.stretchHistogram(imp, 0.35);
	}
    
 // values:    an array of numbers that will be modified in place
 // smoothing: the strength of the smoothing filter; 1=no change, larger values smoothes more
    // Taken from : http://phrogz.net/js/framerate-independent-low-pass-filter.html
 private void smoothArray(double[] values, double smoothing){
   double value = values[0]; // start with the first input
   for (int i=1, len=values.length; i<len; ++i){
     double currentValue = values[i];
     value += (currentValue - value) / smoothing;
     values[i] = value;
   }
 }
}
