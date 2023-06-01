import java.util.List;
import java.util.Optional;
import java.lang.IndexOutOfBoundsException;

/**
 * Derived from
 * @author enjmiah
 */
public class Mesh {
    private final ImList<Vertex> vertices;
    private final ImList<Face> faces;
    private final ImList<HalfEdge> edges;

    /**
     * Constructs a mesh based on the Wavefront OBJ format.
     * Note that vertices are indexed from 0.
     *
     * @param points a {@code List} of {@code Point} for each vertex in the mesh.
     * @param faces  a {@code List} of a {@code List} of integers for the indices of each vertex in each face.
     */
    public Mesh(List<? extends Point> points,
            List<? extends List<? extends Integer>> faces) {
        ImList<Vertex> newVertices = new ImList<Vertex>();
        ImList<Face> newFaces = new ImList<Face>();
        ImList<HalfEdge> newEdges = new ImList<HalfEdge>();
        ImMap<String, HalfEdge> newEdgeMap = new ImMap<String, HalfEdge>();

        for (int i = 0; i < points.size(); ++i) {
            Vertex v = new Vertex(i, points.get(i));
            newVertices = newVertices.add(v);
        }
        
        for (List<? extends Integer> f : faces) {
            ImList<HalfEdge> faceEdges = new ImList<HalfEdge>();
            for (int i = 0; i < f.size(); ++i) {
                int i2 = i < f.size() - 1 ? i + 1 : 0;
                HalfEdge edge = new HalfEdge(newEdges.size());
                Vertex v1 = newVertices.get(f.get(i));
                Vertex v2 = newVertices.get(f.get(i2));
                String key = v1.getId() + ";" + v2.getId();
                newEdgeMap = newEdgeMap.put(key, edge);
                edge.setVertex(v1);
                if (v1.getHalfEdge().isEmpty()) {
                    v1.setHalfEdge(edge);
                }
                Optional<HalfEdge> twin = newEdgeMap.get(
                        v2.getId() + ";" + v1.getId());
                if (twin.isPresent()) {
                    edge.setTwin(twin.get());
                    twin.get().setTwin(edge);
                }
                newEdges = newEdges.add(edge);
                faceEdges = faceEdges.add(edge);
            }
            
            Face face = new Face(newFaces.size());
            face.setHalfEdge(faceEdges.get(0));
            newFaces = newFaces.add(face);
            for (HalfEdge currEdge : faceEdges) {
                currEdge.setFace(face);
            }
            int len = faceEdges.size();
            for (int i = 0; i < len; ++i) {
                faceEdges.get(i).setNext(faceEdges.get((i + 1) % len));
                faceEdges.get(i).setPrev(faceEdges.get((i - 1 + len) % len));
            }
        }

        for (int i = 0, len = newEdges.size(); i < len; ++i) {
            HalfEdge edge = newEdges.get(i);
            if (edge.getTwin().isEmpty()) {
                HalfEdge newHalfEdge = new HalfEdge(newEdges.size());
                Vertex v1 = edge.getNext().get().getVertex().get();
                Vertex v2 = edge.getVertex().get();
                String key = v1.getId() + ";" + v2.getId();
                newEdgeMap = newEdgeMap.put(key, newHalfEdge);
                newHalfEdge.setVertex(v1);
                if (v1.getHalfEdge().isEmpty()) {
                    v1.setHalfEdge(newHalfEdge);
                }
                Optional<HalfEdge> twin = newEdgeMap.get(
                        v2.getId() + ";" + v1.getId());
                if (twin.isPresent()) {
                    newHalfEdge.setTwin(twin.get());
                    twin.get().setTwin(newHalfEdge);
                }
                newEdges = newEdges.add(newHalfEdge);
            }
        }

        for (HalfEdge edge : newEdges) {
            if (edge.getFace().isEmpty()) {
                Optional<HalfEdge> next = edge.getTwin();
                if (next.isPresent()) {
                    do {
                        Optional<HalfEdge> newNext = next.get().getPrev();
                        if (newNext.isPresent()) {
                            next = newNext.get().getTwin();
                        }
                    } while (next.get().getFace().isPresent());
                    edge.setNext(next.get());
                    next.get().setPrev(edge);
                }
            }
        }

        this.vertices = newVertices;
        this.faces = newFaces;
        this.edges = newEdges;
    }

    private Mesh(ImList<Vertex> vertices, ImList<Face> faces, ImList<HalfEdge> edges) {
        this.vertices = vertices;
        this.faces = faces;
        this.edges = edges;
    }

    public void check() {
        for (HalfEdge e : this.edges) {
            if (e.getTwin().isPresent() &&
                    !e.equals(e.getTwin().get().getTwin().get())) {
                throw new Error("edge: twin inconsistent");
            }
            if (e.getNext().isPresent() &&
                    !e.getFace().equals(e.getNext().get().getFace())) {
                throw new Error("edge: next face inconsistent");
            }
            if (e.getPrev().isPresent() &&
                    !e.getFace().equals(e.getPrev().get().getFace())) {
                throw new Error("edge: prev face inconsistent");
            }
            if (e.getPrev().isPresent() &&
                    !e.equals(e.getPrev().get().getNext().get())) {
                throw new Error("edge: next inconsistent");
            }
            if (e.getNext().isPresent() &&
                    !e.equals(e.getNext().get().getPrev().get())) {
                throw new Error("edge: prev inconsistent");
            }
        }

        for (Vertex v : this.vertices) {
            if (v.getHalfEdge().isPresent() &&
                    !v.equals(((HalfEdge) v.getHalfEdge().get()).getVertex().get())) {
                throw new Error("vertex: edge inconsistent");
            }
        }

        for (Face f : this.faces) {
            if (f.getHalfEdge().isPresent() &&
                    !f.equals(((HalfEdge) f.getHalfEdge().get()).getFace().get())) {
                throw new Error("face: edge inconsistent");
            }
        }
    }

    public Mesh copy() {
        ImList<Vertex> vertices = new ImList<Vertex>();
        ImList<Face> faces = new ImList<Face>();
        ImList<HalfEdge> edges = new ImList<HalfEdge>();
        for (Vertex v : this.vertices) {
            vertices = vertices.add(v.copy());
        }
        for (Face f : this.faces) {
            faces = faces.add(f.copy());
        }
        for (HalfEdge e: this.edges) {
            edges = edges.add(e.copy());
        }
        Mesh mesh = new Mesh(vertices, faces, edges);
        
        for (int i = 0; i < mesh.vertices.size(); ++i) {
            mesh.vertices.get(i).setHalfEdge(
                    mesh.edges.get(this.edges.indexOf(
                            (HalfEdge)this.vertices.get(i).getHalfEdge().get())));
        }
        for (int i = 0; i < mesh.faces.size(); ++i) {
            mesh.faces.get(i).setHalfEdge(
                    mesh.edges.get(this.edges.indexOf(
                            (HalfEdge)this.faces.get(i).getHalfEdge().get())));
        }
        for (int i = 0; i < mesh.edges.size(); ++i) {
            HalfEdge e = mesh.edges.get(i);
            HalfEdge og = this.edges.get(i);
            e.setNext(mesh.edges.get(
                        this.edges.indexOf(og.getNext().get())));
            e.setPrev(mesh.edges.get(
                        this.edges.indexOf(og.getPrev().get())));
            e.setTwin(mesh.edges.get(
                        this.edges.indexOf(og.getTwin().get())));
            e.setVertex(mesh.vertices.get(
                        this.vertices.indexOf(og.getVertex().get())));
            if (og.getFace().isPresent()) {
                e.setFace(mesh.faces.get(
                            this.faces.indexOf(og.getFace().get())));
            }
        }
        return mesh;
    }

    public Mesh moveVertex(int i, Point p) {
        try {
            this.vertices.get(i);
        } catch (IndexOutOfBoundsException e) {
            return this;
        }
        Mesh mesh = this.copy();
        Vertex v1 = mesh.vertices.get(i);
        Vertex v2 = v1.move(p);
        for (HalfEdge e : mesh.edges) {
            if (e.getVertex().get().equals(v1)) {
                e.setVertex(v2);
            }
        }
        mesh = new Mesh(mesh.vertices.set(i, v2),
                mesh.faces, mesh.edges);
        mesh.check();
        return mesh;
    }

    public Mesh splitEdgeMakeVert(int edgeIndex, Point vertexPoint) {
        try {
            this.edges.get(edgeIndex);
        } catch (IndexOutOfBoundsException e) {
            return this;
        }
        Vertex newVertex = new Vertex(this.vertices.size(), vertexPoint);
        HalfEdge v2In = new HalfEdge(this.edges.size());
        HalfEdge v1In = new HalfEdge(this.edges.size() + 1);
        Mesh mesh = this.copy();
        HalfEdge v1Out = mesh.edges.get(edgeIndex);
        HalfEdge v2Out = v1Out.getTwin().get();
        Vertex v1 = v1Out.getVertex().get();
        Vertex v2 = v2Out.getVertex().get();

        newVertex.setHalfEdge(v2In);
        v1Out.getNext().get().setPrev(v2In);
        v2In.setNext(v1Out.getNext().get());
        v2Out.getNext().get().setPrev(v1In);
        v1In.setNext(v2Out.getNext().get());
        v1Out.setNext(v2In);
        v1Out.setTwin(v1In);
        v2Out.setNext(v1In);
        v2Out.setTwin(v2In);
        v2In.setVertex(newVertex);
        v2In.setPrev(v1Out);
        v2In.setTwin(v2Out);
        if (v1Out.getFace().isPresent()) {
            v2In.setFace(v1Out.getFace().get());
        }
        v1In.setVertex(newVertex);
        v1In.setPrev(v2Out);
        v1In.setTwin(v1Out);
        if (v2Out.getFace().isPresent()) {
            v1In.setFace(v2Out.getFace().get());
        }
    
        mesh = new Mesh(mesh.vertices.add(newVertex), mesh.faces,
                mesh.edges.addAll(List.of(v2In, v1In)));
        mesh.check();
        return mesh;
    }
    
    public Mesh joinEdgeKillVert(int i) {
        try {
            this.vertices.get(i);
        } catch (IndexOutOfBoundsException e) {
            return this;
        }
        Vertex v = this.vertices.get(i);
        int count = 0;
        HalfEdge start = (HalfEdge) v.getHalfEdge().get();
        HalfEdge curr = start;
        do {
            ++count;
            curr = curr.getNext().get();
        } while (!start.equals(curr));
        if (getVertexHalfEdges(v).size() > 2 || count <= 3) {
            return this;
        }

        Mesh mesh = this.copy();
        Vertex v2 = mesh.vertices.get(i);
        HalfEdge v2Out = (HalfEdge) mesh.vertices.get(i).getHalfEdge().get();
        HalfEdge v2In = v2Out.getTwin().get();
        HalfEdge v1Out = v2Out.getPrev().get();
        HalfEdge v1In = v1Out.getTwin().get();
        Vertex v1 = v1Out.getVertex().get();
        Vertex v3 = v2In.getVertex().get();
        
        v1Out.setNext(v2Out.getNext().get());
        v2Out.getNext().get().setPrev(v1Out);
        v2In.setNext(v1In.getNext().get());
        v1In.getNext().get().setPrev(v2In);
        v1Out.setTwin(v2In);
        v2In.setTwin(v1Out);
        if (v1Out.getFace().isPresent()) {
            v1Out.getFace().get().setHalfEdge(v1Out);
        }
        if (v2In.getFace().isPresent()) {
            v2In.getFace().get().setHalfEdge(v2In);
        }

        ImList<Vertex> vertices = mesh.vertices.remove(i);
        ImList<HalfEdge> edges = new ImList<HalfEdge>();
        for (HalfEdge e : mesh.edges) {
            if (!e.equals(v2Out) && !e.equals(v1In)) {
                edges = edges.add(e);
            }
        }
        mesh = new Mesh(vertices, mesh.faces, edges);
        mesh.check();
        return mesh;
    }

    public Mesh splitFaceMakeEdge(int faceIndex, int v1Index, int v2Index) {
        try {
            Face f = this.faces.get(faceIndex);
            getFaceHalfEdges(f).get(v1Index);
            getFaceHalfEdges(f).get(v2Index);
        } catch (IndexOutOfBoundsException e) {
            return this;
        }
        if (Math.abs(v1Index - v2Index) < 2 ||
                getFaceHalfEdges(faceIndex).size() <= 3) {
            return this;
        }
        Face newFace = new Face(this.faces.size());
        HalfEdge newEdge1 = new HalfEdge(this.edges.size());
        HalfEdge newEdge2 = new HalfEdge(this.edges.size() + 1);
        Mesh mesh = this.copy();
        Face f = mesh.faces.get(faceIndex);
        Vertex v1 = getFaceHalfEdges(f)
            .get(v1Index).getVertex().get();
        Vertex v2 = getFaceHalfEdges(f)
            .get(v2Index).getVertex().get();
        HalfEdge v1Out = (HalfEdge) f.getHalfEdge().get();
        HalfEdge v1In = v1Out.getPrev().get();
        for (int i = 0; i < v1Index; ++i) {
            v1Out = v1Out.getNext().get();
            v1In = v1Out.getPrev().get();
        }
        HalfEdge v2Out = (HalfEdge) f.getHalfEdge().get();
        HalfEdge v2In = v2Out.getPrev().get();
        for (int i = 0; i < v2Index; ++i) {
            v2Out = v2Out.getNext().get();
            v2In = v2Out.getPrev().get();
        }
        Face oldFace = mesh.faces.get(faceIndex);
        
        oldFace.setHalfEdge(newEdge1);
        newFace.setHalfEdge(newEdge2);

        v1In.setNext(newEdge1);
        v1Out.setPrev(newEdge2);
        v1Out.setFace(newFace);
        v2In.setNext(newEdge2);
        v2In.setFace(newFace);
        v2Out.setPrev(newEdge1);
        newEdge1.setPrev(v1In);
        newEdge1.setNext(v2Out);
        newEdge1.setTwin(newEdge2);
        newEdge1.setVertex(v1);
        newEdge1.setFace(oldFace);
        newEdge2.setPrev(v2In);
        newEdge2.setNext(v1Out);
        newEdge2.setTwin(newEdge1);
        newEdge2.setVertex(v2);
        newEdge2.setFace(newFace);

        mesh = new Mesh(mesh.vertices, mesh.faces.add(newFace),
                mesh.edges.addAll(List.of(newEdge1, newEdge2)));
        mesh.check();
        return mesh;
    }
 
    public Mesh joinFaceKillEdge(int i) {
        Mesh mesh = this.copy();
        try {
            mesh.edges.get(i);
        } catch (IndexOutOfBoundsException e) {
            return mesh;
        }
        HalfEdge v1Out = mesh.edges.get(i);
        HalfEdge v2Out = v1Out.getTwin().get();
        if (v1Out.getFace().isEmpty() || v2Out.getFace().isEmpty()) {
            return this;
        }
        Face f1 = v1Out.getFace().get();
        Face f2 = v2Out.getFace().get();
        int count = 0;
        for (HalfEdge e : getFaceHalfEdges(f1)) {
            if (e.getFace().get().equals(f1) &&
                    e.getTwin().get().getFace().isPresent() &&
                    e.getTwin().get().getFace().get().equals(f2)) {
                count++;
            }
        }
        if (count > 1) {
            return this;
        }
        Vertex v1 = v1Out.getVertex().get();
        Vertex v2 = v2Out.getVertex().get();

        for (HalfEdge e : getFaceHalfEdges(f2)) {
            e.setFace(f1);
        }
        v1Out.getPrev().get().setNext(v2Out.getNext().get());
        v1Out.getNext().get().setPrev(v2Out.getPrev().get());
        v2Out.getPrev().get().setNext(v1Out.getNext().get());
        v2Out.getNext().get().setPrev(v1Out.getPrev().get());
        f1.setHalfEdge(v1Out.getNext().get());
        v1.setHalfEdge(v2Out.getNext().get());
        v2.setHalfEdge(v1Out.getNext().get());

        int j = mesh.edges.indexOf(v2Out);
        int index1, index2 = -1;
        if (i < j) {
            index1 = i;
            index2 = j;
        } else {
            index1 = j;
            index2 = i;
        }
        int f2Index = mesh.faces.indexOf(f2);
        mesh = new Mesh(mesh.vertices, mesh.faces.remove(f2Index),
                mesh.edges.remove(index1).remove(index2 - 1));
        mesh.check();
        return mesh;
    }

    public ImList<HalfEdge> getFaceHalfEdges(int i) {
        try {
            this.faces.get(i);
        } catch (IndexOutOfBoundsException e) {
            return new ImList<HalfEdge>();
        }
        Mesh mesh = this.copy();
        Face f = mesh.faces.get(i);
        return mesh.getFaceHalfEdges(f);
    }
    
    public ImList<HalfEdge> getVertexHalfEdges(int i) {
        try {
            this.vertices.get(i);
        } catch (IndexOutOfBoundsException e) {
            return new ImList<HalfEdge>();
        }
        Mesh mesh = this.copy();
        Vertex v = mesh.vertices.get(i);
        return mesh.getVertexHalfEdges(v);
    }

    private ImList<HalfEdge> getFaceHalfEdges(Face f) {
        if (f.getHalfEdge().isEmpty()) {
            return new ImList<HalfEdge>();
        }
        ImList<HalfEdge> edges = new ImList<HalfEdge>();
        HalfEdge start = (HalfEdge) f.getHalfEdge().get();
        HalfEdge curr = start;
        do {
            edges = edges.add(curr);
            curr = curr.getNext().get();
        } while (!start.equals(curr));
        return edges;
    }
    
    private ImList<HalfEdge> getVertexHalfEdges(Vertex v) {
        if (v.getHalfEdge().isEmpty()) {
            return new ImList<HalfEdge>();
        }
        ImList<HalfEdge> edges = new ImList<HalfEdge>();
        HalfEdge start = (HalfEdge) v.getHalfEdge().get();
        HalfEdge curr = start;
        do {
            edges = edges.add(curr);
            curr = curr.getPrev().get().getTwin().get();
        } while (!start.equals(curr));
        return edges;
    }

    public ImList<Vertex> getVertices() {
        Mesh mesh = this.copy();
        ImList<Vertex> vertices = new ImList<Vertex>();
        for (Vertex v : mesh.vertices) {
            vertices = vertices.add(v);
        }
        return vertices;
    }

    public ImList<Face> getFaces() {
        Mesh mesh = this.copy();
        ImList<Face> faces = new ImList<Face>();
        for (Face f : mesh.faces) {
            faces = faces.add(f);
        }
        return faces;
    }

    public ImList<HalfEdge> getHalfEdges() {
        Mesh mesh = this.copy();
        ImList<HalfEdge> edges = new ImList<HalfEdge>();
        for (HalfEdge e : mesh.edges) {
            edges = edges.add(e);
        }
        return edges;
    }

    @Override
    public String toString() {
        String output = "";
        for (Vertex v : this.vertices) {
            output += v.toString() + "\n";
        }
        for (Face f : this.faces) {
            output += f.toString() + "\n";
        }
        for (HalfEdge e : this.edges) {
            output += e.toString() + "\n";
        }
        return output;
    }
}
