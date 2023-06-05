// Clase que implementa la interfaz remota Seed.
// Actúa como un servidor que ofrece un método remoto para leer los bloques
// del fichero que se está descargando.
// Proporciona un método estático (init) para instanciarla.
// LA FUNCIONALIDAD SE COMPLETA EN LAS 4 FASES TODO 1, TODO 2, TODO 3 y TODO 4
// En las fases 3 y 4 se convertirá en un objeto remoto

package peers;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.LinkedList;
import java.rmi.RemoteException;

import interfaces.Seed;
import interfaces.Leech;
import interfaces.Tracker;
import interfaces.FileInfo;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

// Un Leech guarda en esta clase auxiliar su conocimiento sobre cuál es el
// último bloque descargado por otro Leech.
class LeechInfo {
    Leech leech;    // de qué Leech se trata
    int lastBlock;  // último bloque que sabemos que se ha descargado
    public LeechInfo (Leech l, int nBl) {
        leech = l;
        lastBlock = nBl;
    }
    Leech getLeech() {
        return leech;
    }
    int getLastBlock() {
        return lastBlock;
    }
    void setLastBlock(int nBl) {
        lastBlock = nBl;
    }
};
// Esta clase actúa solo de cliente en las dos primeras fases, pero
// en las dos últimas ejerce también como servidor convirtiéndose en
// un objeto remoto.
public class DownloaderImpl extends UnicastRemoteObject implements Leech{ 
    String name; // nombre del nodo (solo para depurar)
    String file;
    String path;
    int blockSize;
    int numBlocks;
    int lastBlock; // último bloque descargado por este Leech
    Seed seed;
    FileInfo fInfo;
    LeechInfo[] lInfo;
    RandomAccessFile raf;
    int contDownload;
    LinkedList<Leech> leechNotfRequ;

    public DownloaderImpl(String n, String f, FileInfo finf) throws RemoteException, IOException {
        name = n;
        file = f;
        path = name + "/" + file;
        blockSize = finf.getBlockSize();
        numBlocks = finf.getNumBlocks();
        seed = finf.getSeed();
        fInfo = finf;
        lastBlock = -1;
        contDownload = 0;
        leechNotfRequ = new LinkedList<Leech>();

        // abre el fichero para escritura
        raf= new RandomAccessFile(new File(path), "rw");
        raf.setLength(0);

        // obtiene el número del último bloque descargado por leeches
	    // anteriores (contenidos en FileInfo) usando getLastBlockNumber
        LinkedList<Leech> leechList = fInfo.getLeechList();
        
        lInfo = new LeechInfo[leechList.size()];
        for(Leech e : leechList){
            lInfo[leechList.indexOf(e)] = new LeechInfo(e, e.getLastBlockNumber());
        }
        
        // TODO 4: solicita a esos leeches anteriores usando newLeech
        // que le notifiquen cuando progrese su descarga
        for(Leech e : leechList){
            e.newLeech(this);
        }
    }
    /* métodos locales */
    public int getNumBlocks() {
        return numBlocks;
    }
    public FileInfo getFileInfo() {
        return fInfo;
    }
    // realiza la descarga de un bloque y lo almacena en un fichero local
    public boolean downloadBlock(int numBl) throws RemoteException {
        byte[] buf = null;
        
        while(contDownload<lInfo.length){
            if(numBl<=lInfo[contDownload].getLastBlock()){
                buf = lInfo[contDownload].getLeech().read(numBl);
                contDownload++;
                if(buf!=null){
                    System.out.println(buf.length);
                    try {
                        raf.write(buf);
                        lastBlock = numBl;
                        return true;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }else contDownload++;
        }
        buf = seed.read(numBl);
        if(buf!=null){
            System.out.println(buf.length);
            try {
                raf.write(buf);
                lastBlock = numBl;
            } catch (IOException e) {
                e.printStackTrace();
            }
            contDownload = 0;
        } else return false;
        // TODO 4: Notifica a los leeches posteriores (notifyBlock)
        // que se ha descargado un bloque
        for(Leech e : leechNotfRequ){
            e.notifyBlock(this,numBl);
        }
        return true;
    }
    
    /* métodos remotos que solo se usarán cuando se convierta en
       un objeto remoto en la fase 3 */
 
    // solo para depurar
    public String getName() throws RemoteException {
        return name;
    }
    // prácticamente igual que read del Seed
    public byte [] read(int numBl) throws RemoteException {
        byte [] buf = null;
        System.out.println("downloader read " + numBl);
        // realiza lectura solicitada devolviendo lo leído en buf 
        // Cuidado con último bloque que probablemente no estará completo
        try{
            int numBlockRequested = numBl;
            long fileSizeBytes = new File(path).length();
            // se asegura que el bloque solicitado está dentro del fichero
            if (numBlockRequested < numBlocks) {
                int bufSize = blockSize;
                if (numBlockRequested + 1 == numBlocks) { // último bloque
                    System.out.println("Estoy en el ultimo bloque.");
                    int fragmentSize = (int) (fileSizeBytes % blockSize);
                    System.out.println("fragmentSize: " + fragmentSize);
                    if (fragmentSize > 0) bufSize = fragmentSize;
                    System.out.println("bufSize: " + bufSize);
                }
                System.out.println("bufSize: " + bufSize);
                buf = new byte[bufSize];
                System.out.println(buf.length);
                raf.seek(numBlockRequested * blockSize);
                int breaded =  raf.read(buf); 
                System.out.println("publisher read " + numBl);   
                System.out.println("breaded: " + breaded);
            }
        }catch(IOException e){
            e.printStackTrace();
        }
        return buf;
    } 
    // obtiene cuál es el último bloque descargado por este Leech
    public int getLastBlockNumber() throws RemoteException{
        return lastBlock;
    }
    /* métodos remotos solo para la última fase */
    // leech solicitante será notificado del progreso de la descarga
    public void newLeech(Leech requester) throws RemoteException {
        // TODO 4: añade ese leech a la lista de leeches posteriores
	    // que deben ser notificados
        leechNotfRequ.add(requester);
    }
    // Informa del progreso de la descarga
    public void notifyBlock(Leech l, int nBl) throws RemoteException {
        // TODO 4: actualizamos la información sobre el último bloque
	    // descargado por ese leech
        for(LeechInfo e : lInfo){
            if(e.getLeech().equals(l)){
                e.setLastBlock(nBl);
            }
        }
    }

    // método estático que obtiene del registry una referencia al tracker y
    // obtiene mediante lookupFile la información del fichero especificado
    // creando una instancia de esta clase
    static public DownloaderImpl init(String host, int port, String name, String file) throws RemoteException {
        if (System.getSecurityManager() == null)
            System.setSecurityManager(new SecurityManager());

        DownloaderImpl down = null;
        try {
            // localiza el registry en el host y puerto indicado
            // y obtiene la referencia remota al tracker asignándola
            // a esta variable:
            Registry registry = LocateRegistry.getRegistry(host,port);
            Tracker trck = (Tracker) registry.lookup("BitCascade");
            
            // comprobamos si ha obtenido bien la referencia:
            System.out.println("el nombre del nodo del tracker es: " + trck.getName());
            // obtiene la información del fichero mediante el
	        // método lookupFile del Tracker.
            FileInfo finf = trck.lookupFile(file); // asigna resultado de lookupFile

            if (finf==null) { // comprueba resultado
                // si null: no se ha publicado ese fichero
                System.err.println("Fichero no publicado");
                System.exit(1);
            }
            // crea un objeto de la clase DownloaderImpl
            down = new DownloaderImpl(name, file, finf);

            // usa el método addLeech del tracker para añadirse
            if(trck.addLeech(down, file)){
                System.out.println("downloader added to tracker");
            }else System.out.println("downloader not added to tracker");
        }
        catch (Exception e) {
            System.err.println("Downloader exception:");
            e.printStackTrace();
            System.exit(1);
        }
        return down;
    }
}
