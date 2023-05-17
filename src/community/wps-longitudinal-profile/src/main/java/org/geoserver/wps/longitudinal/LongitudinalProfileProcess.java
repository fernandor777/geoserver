package org.geoserver.wps.longitudinal;

import org.geoserver.catalog.CoverageInfo;
import org.geoserver.config.GeoServer;
import org.geoserver.wps.gs.GeoServerProcess;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.geometry.DirectPosition2D;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.opengis.coverage.grid.GridCoverageReader;
import org.opengis.geometry.DirectPosition;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

@DescribeProcess(
        title = "Longitudinal Profile Process",
        description =
                "Write a nice description for the process."
)
public class LongitudinalProfileProcess implements GeoServerProcess {

    private final GeoServer geoServer;

    public LongitudinalProfileProcess(GeoServer geoServer) {
        this.geoServer = geoServer;
    }

    @DescribeResult(description = "Longitudinal Profile Process result.")
    public LongitudinalProfileProcessResult execute(@DescribeParameter(name = "layer", description = "layer", min = 1) String layer,
                        @DescribeParameter(name = "linestringWkt", description = "linestring wkt", min = 1) String linestringWkt,
                        @DescribeParameter(name = "distance", description = "distance", min = 1) Double distance,
                        @DescribeParameter(name = "outputFormat", description = "outputFormat", min = 1) String outputFormat,
                        @DescribeParameter(name = "projection", description = "projection", min = 1) String projection) {
        // TODO implement this method

        
        CoverageInfo coverageInfo = geoServer.getCatalog().getCoverageByName(layer);
        // get the COverage2d instances from the coverageInfo
        GridCoverage2DReader gridCoverageReader = (GridCoverage2DReader)coverageInfo.getGridCoverageReader(null, null);
        GridCoverage2D gridCoverage2D = gridCoverageReader.read(null);
        
        // for every point in the linestringWkt, get the elevation value of the coverage
        CoordinateReferenceSystem crs = null;
        DirectPosition position = new DirectPosition2D(crs, xCoordinate, yCoordinate);
        double[] elevation = (double[]) gridCoverage2D.evaluate(position);

        return null;
    }

}
