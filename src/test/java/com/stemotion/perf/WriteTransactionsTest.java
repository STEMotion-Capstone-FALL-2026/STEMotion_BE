package com.stemotion.perf;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A service method that changes data must run in one transaction, or a failure halfway leaves
 * half the rows written. ScriptService.requestChanges once lost its @Transactional when a helper
 * was inserted above it; this keeps that from happening silently.
 */
class WriteTransactionsTest {

    private static final Pattern WRITE = Pattern.compile(
        "^(create|update|delete|submit|approve|request|apply|publish|reply|resolve|reopen|start|retry|cancel|"
            + "send|ensure|add|remove|change|record|archive|decide|register|login).*");

    @Test
    void everyWritingServiceMethodIsTransactional() throws Exception {
        var resolver = new PathMatchingResourcePatternResolver();
        var readers = new CachingMetadataReaderFactory(resolver);
        List<String> missing = new ArrayList<>();
        int checked = 0;
        for (var resource : resolver.getResources("classpath*:com/stemotion/domain/**/service/*.class")) {
            var metadata = readers.getMetadataReader(resource).getClassMetadata();
            Class<?> type = Class.forName(metadata.getClassName());
            if (!type.isAnnotationPresent(Service.class)) continue;
            boolean classLevel = AnnotatedElementUtils.hasAnnotation(type, Transactional.class);
            for (Method method : type.getDeclaredMethods()) {
                int mod = method.getModifiers();
                if (!Modifier.isPublic(mod) || Modifier.isStatic(mod) || method.isSynthetic()) continue;
                if (!WRITE.matcher(method.getName()).matches()) continue;
                checked++;
                if (classLevel || AnnotatedElementUtils.hasAnnotation(method, Transactional.class)) continue;
                missing.add(type.getSimpleName() + "." + method.getName());
            }
        }
        // syncDurations is deliberately not one transaction: it waits on TTS/Whisper on the worker
        // and then saves through transactional services; login only reads.
        missing.removeIf(name -> name.equals("NarrationToolsService.syncDurations") || name.endsWith(".login"));
        assertThat(checked).as("write methods found").isGreaterThan(30);
        assertThat(missing).as("public write methods without @Transactional").isEmpty();
    }
}
