package sim.app.geo.pedestrianSimulation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.prep.PreparedGeometry;
import com.vividsolutions.jts.geom.prep.PreparedGeometryFactory;
import com.vividsolutions.jts.planargraph.Node;

import sim.field.geo.GeomVectorField;
import sim.util.Bag;
import sim.util.geo.GeomPlanarGraph;
import sim.util.geo.GeomPlanarGraphDirectedEdge;
import sim.util.geo.GeomPlanarGraphEdge;
import sim.util.geo.MasonGeometry;

public class RegionalRouting {
	ArrayList<Integer> visited = new ArrayList<Integer>();
	ArrayList<Node> sequence = new ArrayList<Node>();
	ArrayList<Integer> sequenceGateways = new ArrayList<Integer>();
	ArrayList<Integer> sequenceNodes = new ArrayList<Integer>();
	ArrayList<Integer> badExits = new ArrayList<Integer>();
	Node originNode;
	Node destinationNode;
	Node currentLocation;
	Node previousLocation;
	int currentRegion;
	int targetRegion;
	boolean found = false;
	boolean usingBarriers;
	
	HashMap<Integer,EdgeData> edgesMap;
	HashMap<Integer,NodeData> nodesMap;
	HashMap<Integer,CentroidData> centroidsMap;
	HashMap<Integer,GatewayData> gatewaysMap;
	HashMap<Integer,ArrayList<GatewayData>> exitDistrictsMap;
	HashMap<Integer,GeomPlanarGraph> districtDualMap;
	

	public ArrayList<GeomPlanarGraphDirectedEdge> pathFinderRegions(Node originNode, 
			Node destinationNode, String localHeuristic, PedestrianSimulation state)
	{	
		
		this.edgesMap = state.edgesMap;
		this.nodesMap = state.nodesMap;
		this.gatewaysMap = state.gatewaysMap;
		this.exitDistrictsMap = state.exitDistrictsMap;
		this.districtDualMap = state.districtDualMap;
		this.centroidsMap = state.centroidsMap;
		
		this.originNode = originNode;
	    this.destinationNode = destinationNode;
	    this.usingBarriers = state.usingBarriers;
		currentLocation = originNode;
		currentRegion =  nodesMap.get(currentLocation.getData()).district;
		targetRegion = nodesMap.get(destinationNode.getData()).district;
		visited.add(currentRegion);
		sequence.add(currentLocation);
		sequenceGateways.add((Integer) currentLocation.getData());
		sequenceNodes.add((Integer) currentLocation.getData());
		badExits.clear();
		previousLocation = null;
		
		
		// rough plan
		while (found == false)
		{
			ArrayList<GatewayData> possibleGates = exitDistrictsMap.get(currentRegion);
			HashMap<Integer, Double> validGates = new HashMap<Integer, Double> ();
			HashMap<Integer, Double> otherGates = new HashMap<Integer, Double> ();
			
			double destinationAngle = utilities.angle(currentLocation.getCoordinate(), destinationNode.getCoordinate());
			double distanceTarget = utilities.nodesDistance(currentLocation, destinationNode);

			for (GatewayData gd : possibleGates)
			{	
				if (gd.nodeID == currentLocation.getData()) continue;
				if (badExits.contains(gd.gatewayID)) continue;
				if (visited.contains(gd.regionTo)) continue;
				double gateAngle = utilities.angle(currentLocation.getCoordinate(), gd.node.getCoordinate());
				if ((utilities.nodesDistance(gd.node, destinationNode) > distanceTarget) ||
					(utilities.isInDirection(destinationAngle, gateAngle, 140) == false) || 
					(utilities.isInDirection(destinationAngle, gd.entryAngle, 140) == false))
					{
						double cost = utilities.differenceAngles(gateAngle, destinationAngle); 
						if (cost > 95) continue;
						cost += utilities.differenceAngles(gateAngle, gd.entryAngle);
						otherGates.put(gd.gatewayID, cost);
						continue;
					}
				double cost = utilities.differenceAngles(gateAngle, destinationAngle);
				cost += utilities.differenceAngles(gateAngle, gd.entryAngle);   
				validGates.put(gd.gatewayID, cost);
			}
			
			if (validGates.size() == 0) validGates = otherGates;
			if (validGates.size() == 0) 
			{
				if (previousLocation != null)
					{
						badExits.add(sequenceGateways.get(sequenceGateways.size()-2));
						currentLocation = previousLocation;
						currentRegion = nodesMap.get(previousLocation.getData()).district;
						sequence.remove(sequence.size() - 1);
						sequence.remove(sequence.size() - 1);
						sequenceGateways.remove(sequenceGateways.size()-1);
						sequenceGateways.remove(sequenceGateways.size()-1);
						sequenceNodes.remove(sequenceNodes.size()-1);
						sequenceNodes.remove(sequenceNodes.size()-1);
						visited.remove(visited.size()-1);
						previousLocation = null;
						continue;
					}
					
		        if (localHeuristic == "roadDistance")
		        {
		        	return routePlanning.routeDistanceShortestPath(originNode, destinationNode, null, 
		        			state, "dijkstra").edges;
		        }
			        
		        else if (localHeuristic == "angularChange")
		        {
		        	Node originNodeDual = utilities.getDualNode(originNode, PedestrianSimulation.dualNetwork);
					Node destinationNodeDual = utilities.getDualNode(destinationNode, PedestrianSimulation.dualNetwork);
		        	return routePlanning.angularChangeShortestPath(originNodeDual, destinationNodeDual, null, 
		        			state, "dijkstra").edges;
		        }
					
			}
			
			Map<Integer, Double> validSorted = utilities.sortByValue(validGates); 
			Iterator<Entry<Integer, Double>> it = validSorted.entrySet().iterator();
			
			// sequence formulation
			
			while (it.hasNext())
			{
				Map.Entry<Integer, Double> pair = (Map.Entry<Integer, Double>)it.next();
				Integer exitID = pair.getKey();
				Node exitNode = gatewaysMap.get(exitID).node;
				
				Integer entryID = gatewaysMap.get(exitID).entryID;
			  	NodeData nd = nodesMap.get(entryID);
			  	sequenceGateways.add(exitID);
			  	sequenceGateways.add(entryID);
			  	sequenceNodes.add(gatewaysMap.get(exitID).nodeID);
			  	sequenceNodes.add(entryID);
	  			visited.add(nd.district);
	  			previousLocation = currentLocation;
	  			currentLocation = nd.node;
	  			currentRegion = nd.district;
	  			sequence.add(exitNode);
	  			sequence.add(nd.node);
	  			break;
			}
			
			if (currentRegion == targetRegion)
			{
				found = true;
				sequence.add(destinationNode);
				sequenceGateways.add(null);
				break;
			}
		}
		
		//sub-goals and barriers - navigation
		
		ArrayList<Node> newSequence = new ArrayList<Node>();
		newSequence = sequence;
		ArrayList<GeomPlanarGraphDirectedEdge> completePath =  new ArrayList<GeomPlanarGraphDirectedEdge>();
		
		for (Node g: sequence)
		{
			int indexOf = sequence.indexOf(g);
			if ((!usingBarriers) || (indexOf%2 != 0)) continue; //entry gateway,
			
			if (usingBarriers)
			{ 
				//check if there are good barriers in line of movement
				LineString l = utilities.LineStringBetweenNodes(g, destinationNode);			
				GeomVectorField rivers = utilities.filterGeomVectorField(PedestrianSimulation.barriers, "barrier_type", "waterways", "equal");
				Bag intersectingRivers = rivers.getTouchingObjects(utilities.MasonGeometryFromGeometry ((Geometry) l)); 
				
				MasonGeometry farthest = null;
				Node subGoal = null;
				EdgeData edgeGoal = null;
				
				if (intersectingRivers.size() >= 1)
				{
					//look for orienting barriers
					Node nextExit = sequence.get(indexOf+1);
					double distance = 0.0;
					
					for (Object i:intersectingRivers)
					{
						MasonGeometry geoBarrier = (MasonGeometry) i;
						Coordinate intersection = l.intersection(geoBarrier.getGeometry()).getCoordinate();
						double distanceIntersection = utilities.euclideanDistance(g.getCoordinate(), intersection);
						if (distanceIntersection > utilities.euclideanDistance(g.getCoordinate(), nextExit.getCoordinate())) continue;
						else if(distanceIntersection > distance)
						{
							farthest = geoBarrier;	
							distance = distanceIntersection;
						}
					}
				}
				
				if (farthest != null)
				{
					int barrierID = farthest.getIntegerAttribute("barrierID");
					GeomVectorField filteryByDistricts = utilities.filterGeomVectorField(PedestrianSimulation.roads, "district",
							nodesMap.get(g).district, "equal");

					double distance = Double.MAX_VALUE;
					for (Object p: filteryByDistricts.getGeometries())
					{
						MasonGeometry ed = (MasonGeometry) p;
						Integer edgeID = ed.getIntegerAttribute("edgeID");
						List<Integer> positiveBarriers = edgesMap.get(edgeID).positiveBarriers;
						if ((positiveBarriers == null) || (positiveBarriers.contains(barrierID))) continue;
						double distanceEdge = utilities.euclideanDistance(g.getCoordinate(), ed.geometry.getCentroid().getCoordinate());
						if (distanceEdge < distance) edgeGoal = edgesMap.get(edgeID);
					}
				}
				if (edgeGoal == null) continue;
				else
				{
					Node fromNode = nodesMap.get(edgeGoal.fromNode).node;
					Node toNode = nodesMap.get(edgeGoal.toNode).node;
					if ((utilities.nodesDistance(nodesMap.get(g).node, fromNode)) < 
							(utilities.nodesDistance(nodesMap.get(g).node, toNode))) subGoal = fromNode;
					else subGoal = toNode;
				}
				newSequence.add(indexOf+1, subGoal);
			}
			
			ArrayList<GeomPlanarGraphDirectedEdge> resultPartial =  new ArrayList<GeomPlanarGraphDirectedEdge>();
			Node primalOrigin = null;
			Node primalDestination = null;
			Node dualOrigin = null;
			Node dualDestination = null;

			if (indexOf == 0) //start
			{
				primalOrigin = g;
				dualOrigin = utilities.getDualNode(primalOrigin, PedestrianSimulation.dualNetwork);
			}
			
			if (indexOf < sequence.size()-2)
			{
				int exitID = (int) sequenceGateways.get(indexOf+1);
				int connectingEdgeID = gatewaysMap.get(exitID).edgeID;
				primalDestination = g;
				GeomPlanarGraphEdge connectingEdge = edgesMap.get(connectingEdgeID).planarEdge;
				dualDestination = PedestrianSimulation.dualNetwork.findNode(connectingEdge.getLine().getCentroid().
				getCoordinate());
			}
			else 
			{
				primalDestination = g; // actual final destination
				dualDestination = utilities.getDualNode(sequence.get(sequence.size()-1), PedestrianSimulation.dualNetwork);
			}
			if (localHeuristic == "roadDistance") resultPartial = routePlanning.routeDistanceShortestPath(primalOrigin,
					primalDestination, null, state, "dijkstra").edges;
			else	resultPartial = routePlanning.angularChangeShortestPath(dualOrigin, dualDestination, null, state, "dijkstra").edges;
			
			primalOrigin = primalDestination;
			dualOrigin = dualDestination;
			completePath.addAll(resultPartial);
		}
		return completePath;	
	}
}
