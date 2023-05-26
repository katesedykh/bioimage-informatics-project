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

        double[] measurements = new double[frames];

        for (int t = 1; t <= frames; t++) {
            imp.setPosition(DIC_channel, 1, t);
            ImagePlus DIC_stack = new Duplicator().run(imp);
            DIC_stack.setTitle("DIC_stack_" + t);

            IJ.run(DIC_stack, "32-bit", ""); 
            IJ.run(DIC_stack, "Variance...", "radius=5");

            Strel se = Strel.Shape.DISK.fromRadius(5);
            ImageProcessor ip = DIC_stack.getProcessor();
            for (int i = 0; i < 5; i++) {
                ip = Morphology.closing(ip, se);
            }
            DIC_stack.setProcessor(ip);
            
            //IJ.run(DIC_stack, "Invert", "stack");
            IJ.setAutoThreshold(DIC_stack, "Huang dark");
            IJ.run(DIC_stack, "Convert to Mask", "method=Huang background=Dark black");
            //IJ.setAutoThreshold(DIC_stack, "Default dark");
            //IJ.run(DIC_stack, "Convert to Mask", "stack");
            IJ.run(DIC_stack, "Analyze Particles...", "size=20-Infinity circularity=0.50-1.00 show=Nothing clear add");           
            
            // show the final DIC_stack with segmentation outlines
            ImagePlus originalStack = new Duplicator().run(imp);
            originalStack.setTitle("Original_stack_" + t);
            ImageCalculator ic = new ImageCalculator();
            ImagePlus outlinedStack = ic.run("Subtract create stack", originalStack, DIC_stack);
            outlinedStack.show();
            
            imp.setPosition(Fluorescence_channel, 1, t);
            ImagePlus Fluorescence_stack = new Duplicator().run(imp);
            Fluorescence_stack.setTitle("Fluorescence_stack_" + t);
            // Subtract Background
            IJ.run(Fluorescence_stack, "Subtract Background...", "rolling=50");

            RoiManager roiManager = RoiManager.getInstance();
            if (roiManager == null) roiManager = new RoiManager();
            //roiManager.select(0);
            
            // get the ROIs for this frame
            Roi[] rois = roiManager.getRoisAsArray();
            
            // if there are ROIs for this frame
            if(rois != null && rois.length > 0) {
                // Set the ROI to the Fluorescence_stack
                Fluorescence_stack.setRoi(rois[rois.length - 1]);

                IJ.run(Fluorescence_stack, "Measure", "");
                ResultsTable rt = ResultsTable.getResultsTable();
                //double fluorescenceIntensity = rt.getValue("Mean", rt.getCounter() - 1);
                double mean = rt.getValue("Mean", rt.getCounter() - 1);
                double sum = rt.getValue("Sum", rt.getCounter() - 1);
                double stddev = rt.getValue("StdDev", rt.getCounter() - 1);
                double area = rt.getValue("Area", rt.getCounter() - 1);
                double skewness = rt.getValue("Skewness", rt.getCounter() - 1);
                double kurtosis = rt.getValue("Kurtosis", rt.getCounter() - 1);

                // compute index of condensation
                double index = (mean + sum + stddev + area + skewness + kurtosis) / 6;

                measurements[t - 1] = index;
            }


            
           
            //DIC_stack.changes = false;
            //DIC_stack.close();
            //Fluorescence_stack.changes = false;
            //Fluorescence_stack.close();

            roiManager.reset();
        }

        ResultsTable measurementsTable = new ResultsTable();
        for (int i = 0; i < measurements.length; i++) {
            measurementsTable.incrementCounter();
            measurementsTable.addValue("Index", measurements[i]);
        }

        measurementsTable.show("Chromatine Condensation Index Results");
        
        // After filling up the measurements array
        double[] frameNumbers = new double[frames];
        for (int i = 0; i < frames; i++) {
            frameNumbers[i] = i + 1;
        }
        float[] measurementsFloat = new float[measurements.length];
        for (int i = 0; i < measurements.length; i++) {
            measurementsFloat[i] = (float) measurements[i];
        }

        float[] framesFloat = new float[frames];
        for (int i = 0; i < frames; i++) {
            framesFloat[i] = (float) (i + 1);
        }

        Plot plot = new Plot("Chromatine Condensation Index", "Frame", "Value");
        plot.addPoints(framesFloat, measurementsFloat, Plot.LINE);
        plot.addPoints(framesFloat, measurementsFloat, Plot.CIRCLE);
        plot.show();

    }
}
