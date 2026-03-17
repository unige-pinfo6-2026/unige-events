error id: file://<WORKSPACE>/src/main/java/ch/unige/events/service/UserService.java
file://<WORKSPACE>/src/main/java/ch/unige/events/service/UserService.java
### com.thoughtworks.qdox.parser.ParseException: syntax error @[3,11]

error in qdox parser
file content:
```java
offset: 45
uri: file://<WORKSPACE>/src/main/java/ch/unige/events/service/UserService.java
text:
```scala
package ch.unige.events.service;

import ch..@@dto.UpdateProfileRequest;
import com.example.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import java.util.UUID;

@ApplicationScoped
public class UserService {

    /**
     * Appelé à chaque requête authentifiée.
     * Crée le profil si c'est la 1ère connexion.
     */
    @Transactional
    public User getOrCreateUser(String email) {
        return User.findByEmail(email).orElseGet(() -> {
            User newUser = new User();
            newUser.email = email;
            newUser.displayName = email.split("@")[0]; // valeur par défaut
            newUser.persist();
            return newUser;
        });
    }

    public User getPublicProfile(UUID id) {
        User user = (User) User.findByIdOptional(id)
            .orElseThrow(NotFoundException::new);

        if (!user.isProfilePublic) {
            throw new ForbiddenException("This profile is private");
        }
        return user;
    }

    @Transactional
    public User updateMyProfile(String email, UpdateProfileRequest req) {
        User user = User.findByEmail(email)
            .orElseThrow(NotFoundException::new);

        if (req.displayName()    != null) user.displayName    = req.displayName();
        if (req.faculty()        != null) user.faculty        = req.faculty();
        if (req.studyLevel()     != null) user.studyLevel     = req.studyLevel();
        if (req.bio()            != null) user.bio            = req.bio();
        if (req.interests()      != null) user.interests      = req.interests();
        if (req.avatarUrl()      != null) user.avatarUrl      = req.avatarUrl();
        if (req.isProfilePublic()!= null) user.isProfilePublic= req.isProfilePublic();

        return user;
    }
}

```

```



#### Error stacktrace:

```
com.thoughtworks.qdox.parser.impl.Parser.yyerror(Parser.java:2025)
	com.thoughtworks.qdox.parser.impl.Parser.yyparse(Parser.java:2147)
	com.thoughtworks.qdox.parser.impl.Parser.parse(Parser.java:2006)
	com.thoughtworks.qdox.library.SourceLibrary.parse(SourceLibrary.java:232)
	com.thoughtworks.qdox.library.SourceLibrary.parse(SourceLibrary.java:190)
	com.thoughtworks.qdox.library.SourceLibrary.addSource(SourceLibrary.java:94)
	com.thoughtworks.qdox.library.SourceLibrary.addSource(SourceLibrary.java:89)
	com.thoughtworks.qdox.library.SortedClassLibraryBuilder.addSource(SortedClassLibraryBuilder.java:162)
	com.thoughtworks.qdox.JavaProjectBuilder.addSource(JavaProjectBuilder.java:174)
	scala.meta.internal.mtags.JavaMtags.indexRoot(JavaMtags.scala:49)
	scala.meta.internal.metals.SemanticdbDefinition$.foreachWithReturnMtags(SemanticdbDefinition.scala:99)
	scala.meta.internal.metals.Indexer.indexSourceFile(Indexer.scala:560)
	scala.meta.internal.metals.Indexer.$anonfun$reindexWorkspaceSources$3(Indexer.scala:691)
	scala.meta.internal.metals.Indexer.$anonfun$reindexWorkspaceSources$3$adapted(Indexer.scala:688)
	scala.collection.IterableOnceOps.foreach(IterableOnce.scala:630)
	scala.collection.IterableOnceOps.foreach$(IterableOnce.scala:628)
	scala.collection.AbstractIterator.foreach(Iterator.scala:1313)
	scala.meta.internal.metals.Indexer.reindexWorkspaceSources(Indexer.scala:688)
	scala.meta.internal.metals.MetalsLspService.$anonfun$onChange$2(MetalsLspService.scala:940)
	scala.runtime.java8.JFunction0$mcV$sp.apply(JFunction0$mcV$sp.scala:18)
	scala.concurrent.Future$.$anonfun$apply$1(Future.scala:691)
	scala.concurrent.impl.Promise$Transformation.run(Promise.scala:500)
	java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1144)
	java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:642)
	java.base/java.lang.Thread.run(Thread.java:1583)
```
#### Short summary: 

QDox parse error in file://<WORKSPACE>/src/main/java/ch/unige/events/service/UserService.java