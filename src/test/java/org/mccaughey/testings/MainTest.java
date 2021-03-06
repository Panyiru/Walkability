package org.mccaughey.testings;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.geotools.data.DataUtilities;
import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.mccaughey.utilities.GeoJSONUtilities;
import org.mccaughey.utilities.ValidationUtils;
import org.opengis.feature.simple.SimpleFeature;
import org.mccaughey.statistics.ZScore;
import org.mccaughey.connectivity.NetworkBufferOMS;
import org.mccaughey.connectivity.ConnectivityIndex;
import org.mccaughey.density.DwellingDensity;
import org.mccaughey.landuse.LandUseMix;
import org.opengis.feature.simple.SimpleFeatureType;

public class MainTest extends TestCase{

    public MainTest(String testName) {
        super(testName);
    }


    public static Test suite() {
        return new TestSuite(MainTest.class);
    }

    public void testMain(){

        //Set the timer
        long startTIme = System.currentTimeMillis();


        try{
            /*
            Three Input Files:
            1. Road LineFile
            2. Point PointFile
            3. Census Data
            * */
            URL roadsUrl = MainTest.class.getClass().getResource("/psma_cut_projected.geojson.gz");
            URL pointsUrl = MainTest.class.getClass().getResource("/RndmMultiPoint5ptsProjected.json");
            URL landUseURL = MainTest.class.getClass().getResource("/MB_WA_2006_census_projected.shp");
            File landUseShapeFile = new File(landUseURL.toURI());
            FileDataStore landUseDataStore = FileDataStoreFinder.getDataStore(landUseShapeFile);

            List<String> classifications = new ArrayList<String>();
            classifications.add("Parkland");
            classifications.add("Residential");
            classifications.add("Education");
            classifications.add("Commercial");
            classifications.add("Industrial");
            classifications.add("Hospital/Medical");

            /*
            Step 1. Generate Polygons (Regions).
            * */

            NetworkBufferOMS networkBufferOMS = new NetworkBufferOMS();
            networkBufferOMS.network = DataUtilities.source(GeoJSONUtilities.readFeatures(roadsUrl));
            networkBufferOMS.points = DataUtilities.source(GeoJSONUtilities.readFeatures(pointsUrl));
            networkBufferOMS.bufferSize = 100.0;
            networkBufferOMS.distance = 1600.0;
            networkBufferOMS.run();

            //The region is a SimpleFeatureSource object
            SimpleFeatureSource regionSrc = networkBufferOMS.regions;
            System.out.println(regionSrc);

            //Start to build the featureCollections
            List<SimpleFeature> ZScoreFeatures = new ArrayList<SimpleFeature>();

            /*
            Step 2. For each polygon, calculate its connectivity, density as well as Land Use Measure
            * */
            
            SimpleFeatureIterator features = regionSrc.getFeatures().features();
            while (features.hasNext()) {
                SimpleFeature feature = features.next();

                SimpleFeature connectivityFeature = ConnectivityIndex.connectivity(DataUtilities.source(GeoJSONUtilities.readFeatures(roadsUrl)), feature);
                System.out.println("Connectivity: " + String.valueOf(connectivityFeature.getAttribute("Connectivity")));
                Double connectivity = (Double) connectivityFeature.getAttribute("Connectivity");

                SimpleFeature densityFeature = DwellingDensity.averageDensity(landUseDataStore.getFeatureSource(), feature, "TURPOP2006");
                System.out.println("Density: " + densityFeature.getAttribute("AverageDensity"));
                Double density = (Double) densityFeature.getAttribute("AverageDensity");

                SimpleFeature landUseRegionFeature = LandUseMix.summarise(landUseDataStore.getFeatureSource(), feature,
                        classifications, "CATEGORY");
//                for (String classification : classifications) {
//                    System.out.println(classification + " area:" + landUseRegionFeature.getAttribute("LUM_" + classification));
//                }

                System.out.println("Land Use Mix Measure: "
                        + String.valueOf(landUseRegionFeature.getAttribute("LandUseMixMeasure")));
                Double lum = (Double) landUseRegionFeature.getAttribute("LandUseMixMeasure");
                /*
                Rebuild the features and generate new feature collections.
                * */
                ZScoreFeatures.add(Config.buildFeature(feature, connectivity,density,lum));
            }


            features.close();

            /*
            Step 3. Calculate Z-scores.
            * */

            List<String> attributes = new ArrayList();
            attributes.add("Connectivity");
            attributes.add("Density");
            attributes.add("LUM");

            SimpleFeatureCollection ZScoreCollections = DataUtilities.collection(ZScoreFeatures);
            final SimpleFeatureCollection resultingCollection = ZScore.sumZScores(ZScoreCollections.features(), attributes);
//
//            assert(resultingCollection.getSchema().getDescriptor("Connectivity_ZScore")!=null);
//            assert(resultingCollection.getSchema().getDescriptor("Density_ZScore")!=null);
//            assert(resultingCollection.getSchema().getDescriptor("LUM_ZScore")!=null);
//            assert(resultingCollection.getSchema().getDescriptor("SumZScore")!=null);

            SimpleFeatureIterator resultsIt = resultingCollection.features();
            while (resultsIt.hasNext()) {
                final SimpleFeature next = resultsIt.next();
                System.out.println("Connectivity_Zcore: " + next.getAttribute("Connectivity_ZScore"));
                System.out.println("Density_Zcore: " + next.getAttribute("Density_ZScore"));
                System.out.println("LUM_Zcore: " + next.getAttribute("LUM_ZScore"));
                System.out.println("SUMZcore: " + next.getAttribute("SumZScore"));
            }
        }
        catch(URISyntaxException e1){
            System.out.println(e1.getMessage());
        }
        catch(IOException e2){
            System.out.println(e2.getMessage());
        }

        long endTime = System.currentTimeMillis();
        System.out.println("Running Time: " + (double)(endTime-startTIme)/1000 + "s");

    }

}
