package com.stemotion.domain.stem;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
/** Limits actual bytes before JSON parsing, including whitespace; never logs payload. */
@Component
@ConditionalOnProperty(name="app.stem.enabled",havingValue="true")
public class StemRequestLimitFilter extends OncePerRequestFilter {
 private final ObjectMapper json;
 public StemRequestLimitFilter(ObjectMapper json){this.json=json;}
 @Override protected boolean shouldNotFilter(HttpServletRequest r){String path=r.getRequestURI();return !path.startsWith("/api/v1/")||(!path.contains("/stem")&&!path.matches(".*/scripts/[^/]+/(approve|approve-video|request-changes|send-back)$"));}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  byte[] bytes=request.getInputStream().readNBytes(262145);
  if(bytes.length>262144){response.setStatus(400);response.setContentType("application/json");json.writeValue(response.getOutputStream(),Map.of("status",400,"code","STEM_INVALID_FIELD","message","Maximum request body262144bytes","timestamp",java.time.Instant.now().toString(),"details",Map.of("field","request")));return;}
  chain.doFilter(new HttpServletRequestWrapper(request){
   @Override public ServletInputStream getInputStream(){var input=new ByteArrayInputStream(bytes);return new ServletInputStream(){public int read(){return input.read();}public boolean isFinished(){return input.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){throw new UnsupportedOperationException("Synchronous JSON endpoint");}};}
   @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
  },response);
 }
}
