import java.sql.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.util.*;
import com.google.gson.*;
import org.flywaydb.core.Flyway;

public class Phase1Database {
    static String url=System.getenv("DB_URL"), user=System.getenv("DB_USERNAME"), pass=System.getenv("DB_PASSWORD");
    static Path root=Path.of(System.getenv().getOrDefault("STEMOTION_BE_ROOT","STEMotion_BE"));
    static Connection connect(String schema) throws Exception {
        Connection c=DriverManager.getConnection(url,user,pass);
        try(Statement s=c.createStatement()){s.execute("SET search_path TO \""+schema+"\",public");}
        return c;
    }
    static List<String> tables(Connection c) throws Exception {
        var result=new ArrayList<String>();
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename")) {
            while(r.next()) result.add(r.getString(1));
        }
        return result;
    }
    static void migrate(String schema) {
        String scoped=url+(url.contains("?")?"&":"?")+"currentSchema="+schema;
        var result=Flyway.configure().dataSource(scoped,user,pass).defaultSchema(schema).schemas(schema)
            .locations("filesystem:"+root.resolve("src/main/resources/db/migration").toAbsolutePath())
            .baselineOnMigrate(true).baselineVersion("9").load().migrate();
        System.out.println("Migrated "+result.migrationsExecuted+" versions; target="+result.targetSchemaVersion);
    }
    static void backup() throws Exception {
        JsonObject data=new JsonObject();
        JsonObject counts=new JsonObject();
        try(Connection c=connect("public")) {
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setReadOnly(true);c.setAutoCommit(false);
            for(String t:tables(c)) {
                JsonArray rows=new JsonArray();
                try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT row_to_json(t)::text FROM public.\""+t+"\" t")) {
                    while(r.next()) rows.add(JsonParser.parseString(r.getString(1)));
                }
                data.add(t,rows);counts.addProperty(t,rows.size());
            }
            String[] metadata={
                "SELECT row_to_json(c)::text FROM information_schema.columns c WHERE table_schema='public' ORDER BY table_name,ordinal_position",
                "SELECT json_build_object('table',conrelid::regclass::text,'name',conname,'definition',pg_get_constraintdef(oid))::text FROM pg_constraint WHERE connamespace='public'::regnamespace",
                "SELECT row_to_json(i)::text FROM pg_indexes i WHERE schemaname='public'",
                "SELECT json_build_object('type',t.typname,'label',e.enumlabel,'order',e.enumsortorder)::text FROM pg_enum e JOIN pg_type t ON t.oid=e.enumtypid WHERE t.typnamespace='public'::regnamespace",
                "SELECT json_build_object('name',p.proname,'definition',pg_get_functiondef(p.oid))::text FROM pg_proc p WHERE p.pronamespace='public'::regnamespace AND p.prokind='f'"
            };
            for(int i=0;i<metadata.length;i++) {
                JsonArray rows=new JsonArray();try(Statement s=c.createStatement();ResultSet r=s.executeQuery(metadata[i])) {
                    while(r.next()) rows.add(JsonParser.parseString(r.getString(1)));
                }data.add("_metadata_"+i,rows);
            }
            c.rollback();
        }
        byte[] plain=data.toString().getBytes(StandardCharsets.UTF_8);
        var keyGen=KeyGenerator.getInstance("AES");keyGen.init(256);var key=keyGen.generateKey();
        byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);
        var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));
        byte[] encrypted=cipher.doFinal(plain);
        Path folder=root.resolve(".local-backups/phase1");Files.createDirectories(folder);
        String name="before-v10-v13-"+System.currentTimeMillis();
        Files.write(folder.resolve(name+".aes"),encrypted);
        Files.write(folder.resolve(name+".key"),key.getEncoded());Files.write(folder.resolve(name+".iv"),iv);
        cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(Files.readAllBytes(folder.resolve(name+".key")),"AES"),
            new GCMParameterSpec(128,Files.readAllBytes(folder.resolve(name+".iv"))));
        if(!Arrays.equals(plain,cipher.doFinal(Files.readAllBytes(folder.resolve(name+".aes"))))) throw new IllegalStateException("Backup verification failed");
        JsonObject manifest=new JsonObject();manifest.add("counts",counts);
        manifest.addProperty("backup",name+".aes");manifest.addProperty("sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encrypted)));
        manifest.addProperty("verifiedDecryptRoundTrip",true);
        Files.writeString(folder.resolve(name+".manifest.json"),manifest.toString());
        Files.writeString(Path.of(".audit/phase1/backup-name.txt"),name);
        System.out.println("Encrypted backup verified; tables="+counts.size()+"; name="+name);
    }
    public static void main(String[] args) throws Exception {
        String mode=args[0];
        if(mode.equals("backup")){backup();return;}
        if(mode.equals("clone")) {
            String schema="phase1_verify_"+System.currentTimeMillis();
            Files.writeString(Path.of(".audit/phase1/clone-name.txt"),schema);
            try(Connection c=connect("public");Statement s=c.createStatement()) {
                s.execute("CREATE SCHEMA \""+schema+"\"");
                for(String table:tables(c)) {
                    s.execute("CREATE TABLE \""+schema+"\".\""+table+"\" (LIKE public.\""+table+"\" INCLUDING ALL)");
                    if(table.equals("flyway_schema_history"))s.execute("INSERT INTO \""+schema+"\".flyway_schema_history SELECT * FROM public.flyway_schema_history");
                }
            }
            migrate(schema);System.out.println("Isolated schema ready: "+schema);return;
        }
        if(mode.equals("down-clone")) {
            String schema=Files.readString(Path.of(".audit/phase1/clone-name.txt")).trim();
            if(!schema.matches("phase1_verify_[0-9]+"))throw new IllegalArgumentException("Unsafe clone name");
            try(Connection c=connect(schema);Statement s=c.createStatement()) {
                for(String version:List.of("V13__publication_transition.shared","V12__ai_request_tracking.shared",
                        "V11__explicit_review_decisions","V10__clip_artifacts.shared")) {
                    s.execute(Files.readString(root.resolve("docs/rollback/"+version+".down.sql")));
                }
                System.out.println("Real PostgreSQL shared up/down PASS");
                s.execute("DROP SCHEMA \""+schema+"\" CASCADE");
            }return;
        }
        if(mode.equals("migrate-production")) {
            String name=Files.readString(Path.of(".audit/phase1/backup-name.txt")).trim();
            Path manifest=root.resolve(".local-backups/phase1/"+name+".manifest.json");
            if(!Files.exists(manifest)||System.currentTimeMillis()-Files.getLastModifiedTime(manifest).toMillis()>3600000)
                throw new IllegalStateException("A verified fresh encrypted backup is required");
            migrate("public");return;
        }
        throw new IllegalArgumentException("Unknown mode");
    }
}
