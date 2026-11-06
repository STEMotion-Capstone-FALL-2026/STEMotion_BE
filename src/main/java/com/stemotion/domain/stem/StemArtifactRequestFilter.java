package com.stemotion.domain.stem;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Separate P5 transport bounds; never applies the brief cap to frozen render input. */
@Component
@ConditionalOnProperty(name="app.stem.artifact.enabled",havingValue="true")
public class StemArtifactRequestFilter extends OncePerRequestFilter {
    private final ObjectMapper output;
    private final ObjectMapper input=new ObjectMapper(JsonFactory.builder()
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16).maxStringLength(65536).maxNumberLength(1024).build()).build())
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    public StemArtifactRequestFilter(ObjectMapper mapper){output=mapper;}
    @Override protected boolean shouldNotFilter(HttpServletRequest request){
        return !"POST".equals(request.getMethod())||!(request.getRequestURI().equals("/api/v1/render/callback")||request.getRequestURI().matches("/api/v1/scripts/[^/]+/publish"));
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException{
        boolean callback=request.getRequestURI().endsWith("/callback");int limit=callback?1048576:65536;
        byte[] bytes=request.getInputStream().readNBytes(limit+1);
        String field="request",error=null;
        if(bytes.length>limit)error="P5 request byte limit exceeded";
        else try{
            JsonNode tree=input.readTree(bytes);
            if(tree==null||!tree.isObject())error="Expected JSON object";
            else {
                Set<String> allowed=callback?Set.of("jobId","status","progress","stage","videoUrl","errorMessage","captionSync","artifactManifest"):Set.of("targets","title","description","legacyUnverifiedAcknowledged","artifactExpectation","idempotencyKey");
                for(var names=tree.fieldNames();names.hasNext();){String name=names.next();if(!allowed.contains(name)){error="Unknown/server-owned request field";field=name;break;}}
                if(error==null&&tree.hasNonNull("artifactManifest")&&tree.get("artifactManifest").toString().getBytes(StandardCharsets.UTF_8).length>65536){error="Manifest exceeds64KiB";field="artifactManifest";}
                if(error==null&&tree.hasNonNull("videoUrl")&&tree.path("videoUrl").asText().getBytes(StandardCharsets.UTF_8).length>4096){error="Output URL exceeds4096bytes";field="videoUrl";}
            }
        }catch(IOException ex){error="Invalid/bounded JSON request";}
        if(error!=null){response.setStatus(400);response.setContentType("application/json");output.writeValue(response.getOutputStream(),Map.of("status",400,"code","STEM_INVALID_FIELD","message",error,"timestamp",java.time.Instant.now().toString(),"details",Map.of("field",field)));return;}
        chain.doFilter(new HttpServletRequestWrapper(request){
            @Override public ServletInputStream getInputStream(){var stream=new ByteArrayInputStream(bytes);return new ServletInputStream(){public int read(){return stream.read();}public boolean isFinished(){return stream.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){throw new UnsupportedOperationException("Synchronous endpoint");}};}
            @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
        },response);
    }
}
