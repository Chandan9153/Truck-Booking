# TruckConnect Module 1 — Authentication and Security

**For:** A beginner learning step by step, one module at a time
**Stack:** Java 21, Spring Boot 3, Spring Security + JSON Web Token (JWT), Spring Data Java Persistence API (JPA) + Hibernate, MySQL 8
**This module only:** Registration, login, password safety, tokens, and role-based access. No trucks, bookings, or payments yet — those come in later modules.

## How to use this guide

Read it in order, part by part. Do not try to memorise everything in one sitting.

1. Read Part 1 and Part 2 first so you understand *why* the code exists.
2. Then create the project and copy one file at a time, in the order given in Parts 5 and 6.
3. Run the app after all files are in place, then work through the test checklist in Part 9.
4. If something fails, check Part 10 (common beginner mistakes) before changing random code.

> **Important rule for the whole project:** Never store a plain password, and never put a real secret in code. Secrets go in environment variables. You will see exactly how below.

---

## Part 1 — What this module does, and why it comes first

### What this module does

Authentication and Security answers two different questions:

- **Authentication — who are you?** You prove it by registering once, then logging in with your email and password.
- **Authorisation — what are you allowed to do?** After login, your role decides which pages and actions you can use.

This module gives TruckConnect:

- A `users` table in MySQL where every account is saved
- Register and login endpoints
- Passwords stored safely with BCrypt hashing
- A short-lived access token and a longer-lived refresh token after login
- Role-based protection, so a driver cannot do an admin's work by mistake
- Validation, so empty or wrong input is rejected early with a clear message
- Cross-Origin Resource Sharing (CORS) settings, so the React frontend can call the backend

### Why it comes first

Every later module needs to know *who* is making the request:

- A booking must belong to a customer.
- A truck must belong to an owner or a transporter.
- A trip must be assigned to a driver.
- Only an admin should manage all users.

If we built bookings first, we would have no safe way to say "this booking is yours, not someone else's." So Module 1 is the foundation. Once it works, every new module simply asks: "Is this user logged in, and does their role allow this?"

### What this module does not do

To keep this step small and learnable, this module does **not** include:

- Email verification or OTP login
- Forgot-password / password-reset email flow
- Google or social login
- Truck, booking, payment, or location features

Those can be added later without changing the core design here.


---

## Part 2 — The big picture in simple words

Think of TruckConnect like a building with a security guard at the gate.

1. **First visit — register.** You show your details at the gate. The guard writes your name in a register book (the database), but does *not* write your real password — only a scrambled version of it.
2. **Every later visit — login.** You give your email and password. The guard scrambles what you typed and compares it with the scrambled version in the book. If they match, you are really you.
3. **You get two passes.** An **access token** (your day pass, valid about 15 minutes) and a **refresh token** (valid about 7 days, used only to get a new day pass without typing your password again).
4. **Every room checks your pass and your role.** Your token travels with every request. A filter checks the token first, then a role rule checks whether your role may enter that room. A customer can enter the booking room. Only an admin can enter the admin room.

In code, those four steps are:

```text
Register:  React -> POST /api/auth/register -> AuthController
           -> AuthService -> BCrypt hash -> UserRepository -> MySQL

Login:     React -> POST /api/auth/login -> AuthController
           -> AuthService -> check BCrypt hash -> JwtService
           -> returns access token + refresh token

Request:   React sends header  Authorization: Bearer <access token>
           -> JwtAuthFilter verifies token -> SecurityContext knows user + role
           -> SecurityConfig role rule allows or blocks (403 Forbidden)
```

### Key words you will meet again and again

| Word | Simple meaning |
| --- | --- |
| Hashing | Scrambling a password one-way, so the original cannot be read back |
| BCrypt | The hashing method Spring Security uses for passwords |
| JWT | A signed token the server gives after login; the client sends it back on every request |
| Stateless | The server does not keep a login session in memory; the token carries the proof |
| DTO | Data Transfer Object — a small class that defines exactly what data comes in or goes out |
| JPA | The Java standard for saving Java objects into database tables |


---

## Part 3 — The five roles

Every user has exactly **one role** in Module 1. One role keeps the rules simple while you learn. A person who is both an owner and a driver would register two accounts, or the design can grow to multiple roles later.

| Role | Who they are |
| --- | --- |
| `CUSTOMER` | A person or business that needs goods moved and books a truck |
| `OWNER` | A truck owner who lists one or more of their own trucks |
| `DRIVER` | A driver who is assigned trips and updates trip status |
| `TRANSPORTER` | A transport company that manages many trucks and drivers |
| `ADMIN` | The TruckConnect team member who manages the platform |

### What each role can and cannot do

This table is the promise the security rules must enforce. Modules 2 onward will add the truck and booking endpoints themselves; Module 1 already creates the role checks for them.

| Action | CUSTOMER | OWNER | DRIVER | TRANSPORTER | ADMIN |
| --- | :---: | :---: | :---: | :---: | :---: |
| Register and log in | Yes | Yes | Yes | Yes | Yes |
| View own profile | Yes | Yes | Yes | Yes | Yes |
| Book a truck | Yes | No | No | No | No |
| View own bookings | Yes | No | No | No | Yes, all |
| Add / manage own trucks | No | Yes | No | Yes | Yes, all |
| Manage own drivers | No | No | No | Yes | Yes, all |
| Accept / update assigned trip | No | No | Yes | No | No |
| View all users | No | No | No | No | Yes |
| Block / activate a user | No | No | No | No | Yes |
| Change another user's role | No | No | No | No | Yes |

Three safety decisions built into the code:

1. **A user cannot choose to become ADMIN at registration.** If someone sends `role: ADMIN` in the register request, the server rejects it. The first admin is created directly in the database by the project owner (see Part 9), and after that only an admin can promote another user.
2. **No role can see another user's password** — not even ADMIN — because only the BCrypt hash is stored, never the original.
3. **A blocked or deleted check belongs to later admin work.** In Module 1 every registered account is active; an `enabled` field is already on the `User` entity so admin blocking can use it later without a redesign.


---

## Part 4 — Registration, login, and token flow, step by step

### Registration flow

1. The user fills the register form: full name, email, phone, password, and role (`CUSTOMER`, `OWNER`, `DRIVER`, or `TRANSPORTER`).
2. The backend validates the input. For example: email must look like an email, password must be at least 8 characters, phone must be 10 digits starting with 6–9 (Indian mobile format).
3. The backend checks whether that email already exists. If yes, it returns a clear error — no duplicate account is created.
4. The password is hashed with BCrypt. Only the hash is saved.
5. The user row is saved in MySQL, and a success response is returned **without** any token. The user then logs in. Keeping register and login separate makes each step easier to test and understand.

### Login flow

1. The user sends email and password to `POST /api/auth/login`.
2. The backend loads the user by email.
3. BCrypt compares the typed password with the stored hash. BCrypt does not "decrypt" anything — it hashes the typed password the same way and compares the results.
4. If the password is wrong, the backend returns the same generic message — "Invalid email or password" — whether the email was missing or the password was wrong. This stops attackers from learning which emails are registered.
5. If correct, `JwtService` creates two tokens and the backend returns them with the user's id, name, email, and role.

### Access token and refresh token flow

| Token | Lifetime in this module | Used for | Sent where |
| --- | --- | --- | --- |
| Access token | 15 minutes | Every normal API call | `Authorization: Bearer <token>` header |
| Refresh token | 7 days | Getting a new access token only | Only to `POST /api/auth/refresh` |

What happens when the access token expires is the part beginners find most useful:

1. The frontend calls an API and gets `401 Unauthorized` because the access token expired.
2. The frontend silently calls `POST /api/auth/refresh` with the refresh token.
3. `JwtService` verifies the refresh token has a special claim, `type = REFRESH`, so an access token cannot be misused as a refresh token, and vice versa.
4. The backend returns a **new access token and a new refresh token** (this is called rotation — the old refresh token should be discarded by the frontend).
5. The frontend retries the original call. The user notices nothing and never re-types their password.

Simple rules to remember:

- Treat both tokens like passwords. Never log them, never put them in a URL.
- In the React app, keep the access token in memory where possible. If you must persist the refresh token in browser storage for this learning project, understand that any script running on the page could read it — a production app would normally use a secure, `HttpOnly` cookie for the refresh token instead.
- Logout in Module 1 is done on the frontend by deleting both tokens. Because JWTs are stateless, the server cannot cancel a token before it expires — that is one reason the access token is kept short at 15 minutes. A server-side blocklist can be added later if the project needs instant logout.


---

## Part 5 — Project setup

### Folder structure for Module 1

Create a Spring Boot project (for example with Spring Initializr: Java 21, Maven, Spring Boot 3.x) and organise Module 1 like this:

```text
truckconnect-backend/
  pom.xml
  src/main/resources/
    application.properties
  src/main/java/com/truckconnect/
    TruckConnectApplication.java
    auth/
      AuthController.java
      AuthService.java
      dto/
        RegisterRequest.java
        LoginRequest.java
        RefreshTokenRequest.java
        AuthResponse.java
        UserResponse.java
    user/
      User.java
      Role.java
      UserRepository.java
    security/
      JwtService.java
      JwtAuthFilter.java
      SecurityConfig.java
      UserDetailsServiceImpl.java
    common/
      GlobalExceptionHandler.java
      ApiError.java
```

Teacher's note: package names are just folders that keep related files together. `auth` handles register/login, `user` handles the account itself, `security` handles tokens and rules, and `common` handles errors shared by the whole app.

### File 1 — `pom.xml` dependencies

**In simple words:** `pom.xml` is the shopping list. Maven reads it and downloads every library the project needs. You do not write these libraries yourself.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <groupId>com.truckconnect</groupId>
    <artifactId>truckconnect-backend</artifactId>
    <version>0.0.1-SNAPSHOT</version>

    <properties>
        <java.version>21</java.version>
        <jjwt.version>0.12.6</jjwt.version>
    </properties>

    <dependencies>
        <!-- Web: REST controllers -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Security: login rules, BCrypt -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <!-- Validation: @NotBlank, @Email, @Size, etc. -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- JPA + Hibernate: save Java objects to MySQL -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- MySQL driver -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- JWT: create and verify tokens (JJWT) -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>

        <!-- Tests -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

Notice there is **no Lombok** in this list, on purpose. The code below writes getters and setters out in full, so a beginner sees everything and needs no extra IDE plugin. You can add Lombok later once the plain version makes sense.


### File 2 — `application.properties`

**In simple words:** This is the settings file. Notice that every sensitive value uses `${NAME:default}` — Spring reads the real value from an **environment variable**, and the text after the colon is only a local default for non-secret values. The JWT secret has *no* default, so the app will not start with a guessed secret.

First create the database in MySQL:

```sql
CREATE DATABASE truckconnect;
```

```properties
spring.application.name=truckconnect-backend
server.port=${PORT:8080}

# --- MySQL (Hibernate creates/updates the tables from the entities) ---
spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3306/truckconnect}
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# --- JWT (secret comes ONLY from the environment) ---
app.jwt.secret=${JWT_SECRET:}
app.jwt.access-token-ms=${JWT_ACCESS_MS:900000}
app.jwt.refresh-token-ms=${JWT_REFRESH_MS:604800000}

# --- Frontend allowed by CORS (Part 8) ---
app.cors.allowed-origin=${CORS_ORIGIN:http://localhost:5173}
```

Set the environment variables before you run the app. On Linux / macOS:

```bash
export DB_USERNAME=root
export DB_PASSWORD=your-mysql-password
# JWT_SECRET must be Base64 for a key of at least 256 bits:
export JWT_SECRET=$(openssl rand -base64 64)
java -jar target/truckconnect-backend-0.0.1-SNAPSHOT.jar
# or, while developing:  ./mvnw spring-boot:run
```

On Windows PowerShell, use `$env:DB_PASSWORD="..."` and the same names, and generate the secret with a trusted Base64 generator. Use a *different* secret for production. Never commit `.env` files, passwords, or the JWT secret to GitHub; add `.env` to `.gitignore`.

> **Warning:** `spring.jpa.hibernate.ddl-auto=update` is convenient while learning. In production, switch to database migrations with Flyway (`ddl-auto=validate`) so table changes are reviewed, not automatic.


---

## Part 6 — The code, file by file

Copy the files in this order: `Role` → `User` → `UserRepository` → DTOs → `JwtService` → `UserDetailsServiceImpl` → `JwtAuthFilter` → `SecurityConfig` → `AuthService` → `AuthController` → error handler → main class. Each file below says what it does before the code, like a teacher would.

### File 3 — `Role.java` — the list of allowed roles

**In simple words:** An `enum` is a fixed list. A user's role can only be one of these five values — a spelling mistake like `CUSTOMMER` is impossible because Java will not compile it.

```java
package com.truckconnect.user;

public enum Role {
    CUSTOMER,
    OWNER,
    DRIVER,
    TRANSPORTER,
    ADMIN
}
```

### File 4 — `User.java` — one row in the `users` table

**In simple words:** This class *is* the `users` table. Each field becomes a column. `@Entity` tells JPA "save objects of this class in a table." The class also implements Spring Security's `UserDetails`, so Spring can use it directly as the logged-in user. Note the field is called `passwordHash` on purpose — it reminds everyone who reads the code that a plain password must never land there.

```java
package com.truckconnect.user;

import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users",
       uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false, length = 15)
    private String phone;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { this.createdAt = LocalDateTime.now(); }

    // --- Getters and setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // --- UserDetails: how Spring Security sees this user ---
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Spring expects role names like ROLE_CUSTOMER
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    // Account expiry / locking are not used in Module 1
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
}
```

Why `EnumType.STRING` matters: without it, JPA stores the role as a number (0, 1, 2…). Numbers are confusing in the database and dangerous if the enum order ever changes. Storing `CUSTOMER` as text stays readable forever.


### File 5 — `UserRepository.java` — talking to the database

**In simple words:** This interface is the only place that queries the `users` table. Spring Data JPA writes the SQL for you from the method names. `findByEmail` becomes "find the user whose email is…", and `existsByEmail` becomes "does this email already exist?"

```java
package com.truckconnect.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

`Optional` means "maybe a user, maybe nothing." It forces the calling code to handle the not-found case instead of crashing with a null error.

### File 6 — The DTOs — what comes in and what goes out

**In simple words:** The API never exposes the `User` entity directly. If it did, the password hash could leak in a response. DTOs are separate, small classes that carry *only* the fields each request or response needs. Validation rules live on the request DTOs.

`RegisterRequest.java`:

```java
package com.truckconnect.auth.dto;

import com.truckconnect.user.Role;
import jakarta.validation.constraints.*;

public record RegisterRequest(
    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be 2-100 characters")
    String fullName,

    @NotBlank(message = "Email is required")
    @Email(message = "Email format is invalid")
    String email,

    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^[6-9][0-9]{9}$",
             message = "Phone must be a 10-digit Indian mobile number")
    String phone,

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
    String password,

    @NotNull(message = "Role is required")
    Role role
) {}
```

Why password maximum 72? BCrypt only uses the first 72 bytes of a password, so allowing longer passwords would give a false sense of extra security.

`LoginRequest.java`:

```java
package com.truckconnect.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Email format is invalid")
    String email,

    @NotBlank(message = "Password is required")
    String password
) {}
```

`RefreshTokenRequest.java`:

```java
package com.truckconnect.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
    @NotBlank(message = "Refresh token is required")
    String refreshToken
) {}
```

`AuthResponse.java` — returned after login and after refresh:

```java
package com.truckconnect.auth.dto;

import com.truckconnect.user.Role;

public record AuthResponse(
    Long userId,
    String fullName,
    String email,
    Role role,
    String accessToken,
    String refreshToken,
    String tokenType   // always "Bearer"
) {}
```

`UserResponse.java` — the safe public view of a user, with no password field at all:

```java
package com.truckconnect.auth.dto;

import com.truckconnect.user.Role;
import com.truckconnect.user.User;

public record UserResponse(Long id, String fullName, String email,
                           String phone, Role role) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getFullName(),
                user.getEmail(), user.getPhone(), user.getRole());
    }
}
```

These DTOs use Java `record` classes — a short way to write a class that only carries data. Records give you the constructor, getters, `equals`, and `toString` for free.


### File 7 — `JwtService.java` — creating and reading tokens

**In simple words:** This is the token-making machine. It signs tokens with the secret key so nobody can fake one, and it can read a token back and tell you whose it is and whether it has expired. It makes two kinds of tokens and marks each one with a `type` claim so they cannot be swapped.

```java
package com.truckconnect.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.access-token-ms}")
    private long accessTokenMs;

    @Value("${app.jwt.refresh-token-ms}")
    private long refreshTokenMs;

    private SecretKey signingKey() {
        // The JWT_SECRET env var must be Base64 for a key of
        // at least 256 bits. Generate one locally, e.g.:
        // openssl rand -base64 64
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(UserDetails user) {
        return buildToken(user, accessTokenMs, "ACCESS");
    }

    public String generateRefreshToken(UserDetails user) {
        return buildToken(user, refreshTokenMs, "REFRESH");
    }

    private String buildToken(UserDetails user, long lifetimeMs, String type) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.getUsername()) // the email
                .claims(Map.of("type", type))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + lifetimeMs))
                .signWith(signingKey())
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractType(String token) {
        return extractClaim(token, claims -> claims.get("type", String.class));
    }

    public boolean isTokenValid(String token, UserDetails user) {
        return extractUsername(token).equals(user.getUsername())
                && !isExpired(token);
    }

    private boolean isExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return resolver.apply(claims);
    }
}
```

Beginner warning: because the code above decodes the secret as Base64, your `JWT_SECRET` must be a Base64 string for a key of at least 256 bits — for example the output of `openssl rand -base64 64`. A plain short word will make the app fail to start, and that failure is a good thing: a weak secret would let anyone forge tokens.

### File 8 — `UserDetailsServiceImpl.java` — loading a user for Spring Security

**In simple words:** When Spring Security needs a user, it asks this class. The class simply looks the email up in the database.

```java
package com.truckconnect.security;

import com.truckconnect.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found: " + email));
    }
}
```


### File 9 — `JwtAuthFilter.java` — checking the token on every request

**In simple words:** This filter runs *before* every controller. It looks for the `Authorization: Bearer …` header, verifies the token, and if it is good, it tells Spring Security "this request belongs to this user, with this role." If there is no token, it does nothing — the security rules in the next file then block protected URLs. This file is essential: without it, `JwtService` could make tokens that nobody ever checks.

```java
package com.truckconnect.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    public JwtAuthFilter(JwtService jwtService,
                         UserDetailsServiceImpl userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        try {
            String email = jwtService.extractUsername(token);
            boolean isAccessToken = "ACCESS".equals(jwtService.extractType(token));
            boolean notLoggedInYet =
                    SecurityContextHolder.getContext().getAuthentication() == null;

            if (email != null && isAccessToken && notLoggedInYet) {
                UserDetails user = userDetailsService.loadUserByUsername(email);
                if (jwtService.isTokenValid(token, user)) {
                    var auth = new UsernamePasswordAuthenticationToken(
                            user, null, user.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource()
                            .buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        } catch (Exception ex) {
            // Invalid or expired token: leave the request unauthenticated.
            // SecurityConfig will return 401 for protected URLs.
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
```

Note the `type` check: only an `ACCESS` token can open normal APIs. A refresh token sent to a normal endpoint is ignored, so the request is treated as logged out.


### File 10 — `SecurityConfig.java` — the rule book

**In simple words:** This is the guard's rule book. It says which URLs are public, which need a login, and which need a specific role. It also creates the BCrypt password encoder and turns CORS on. The session policy is `STATELESS` — no server session, the JWT is the proof on every request.

```java
package com.truckconnect.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Value("${app.cors.allowed-origin}")
    private String allowedOrigin;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // JWT APIs do not use cookie sessions
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public: anyone can register or log in
                .requestMatchers("/api/auth/register",
                        "/api/auth/login", "/api/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                // Admin only (endpoints themselves come in a later module;
                // the rule is ready now)
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // Any other API needs a logged-in user; finer role
                // rules are added with each new module's endpoints
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
            .addFilterBefore(jwtAuthFilter,
                    UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
```

Two honest notes for later modules:

- The line `.requestMatchers("/api/**").authenticated()` only checks "logged in or not." When Module 2 adds truck endpoints, add specific role lines *above* it, for example `.requestMatchers(HttpMethod.POST, "/api/trucks/**").hasAnyRole("OWNER", "TRANSPORTER", "ADMIN")`. Order matters — Spring uses the first rule that matches.
- For finer checks inside a method, `@EnableMethodSecurity` above lets you write `@PreAuthorize("hasRole('ADMIN')")` on a controller method.

Why is Cross-Site Request Forgery (CSRF) protection disabled here? CSRF attacks abuse browser cookies. This API sends no login cookie — it uses a header token — so the classic CSRF attack does not apply. If refresh tokens later move into cookies, CSRF protection must be turned back on.


### File 11 — `AuthService.java` — the brain of register, login, and refresh

**In simple words:** The controller (next file) only receives requests and returns responses. All real work happens here: checking duplicates, hashing passwords, verifying login, and issuing tokens. Keeping the work in a service makes it reusable and testable.

```java
package com.truckconnect.auth;

import com.truckconnect.auth.dto.*;
import com.truckconnect.security.JwtService;
import com.truckconnect.user.Role;
import com.truckconnect.user.User;
import com.truckconnect.user.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public UserResponse register(RegisterRequest request) {
        if (request.role() == Role.ADMIN) {
            throw new IllegalArgumentException(
                    "ADMIN accounts cannot be created by registration");
        }
        if (userRepository.existsByEmail(
                request.email().trim().toLowerCase())) {
            throw new IllegalArgumentException("Email is already registered");
        }

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setPhone(request.phone().trim());
        // BCrypt hash ONLY. The plain password is never saved.
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setEnabled(true);

        return UserResponse.from(userRepository.save(user));
    }

    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email().trim().toLowerCase(),
                            request.password()));
        } catch (AuthenticationException ex) {
            // Same message for wrong email and wrong password,
            // and for a missing account
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = userRepository.findByEmail(
                        request.email().trim().toLowerCase())
                .orElseThrow(() ->
                        new BadCredentialsException("Invalid email or password"));
        return buildAuthResponse(user);
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        String token = request.refreshToken();
        String email = jwtService.extractUsername(token); // throws if invalid

        if (!"REFRESH".equals(jwtService.extractType(token))) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new BadCredentialsException("Invalid refresh token"));
        if (!jwtService.isTokenValid(token, user)) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        // Rotation: a brand-new access token AND refresh token
        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        UserDetails details = user;
        return new AuthResponse(
                user.getId(), user.getFullName(), user.getEmail(),
                user.getRole(),
                jwtService.generateAccessToken(details),
                jwtService.generateRefreshToken(details),
                "Bearer");
    }
}
```

Look at the `register` method once more. The email is lower-cased before saving and before checking duplicates, so `Ram@Example.com` and `ram@example.com` cannot become two different accounts.

### File 12 — `AuthController.java` — the doors of the API

**In simple words:** A controller is just a door. It receives the HTTP request, lets `@Valid` run the DTO validation, calls the service, and returns the result with the right status code. It contains no business logic of its own.

```java
package com.truckconnect.auth;

import com.truckconnect.auth.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        UserResponse created = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    // A small protected endpoint so a beginner can test tokens at once
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(
            @AuthenticationPrincipal com.truckconnect.user.User user) {
        return ResponseEntity.ok(UserResponse.from(user));
    }
}
```

`GET /api/auth/me` is deliberately included in Module 1. Without at least one protected endpoint, you could not test that tokens and role rules actually work.


### File 13 — Clean error responses

**In simple words:** Without this file, a validation mistake returns a long, ugly Spring error. With it, every error has the same simple shape, which makes the React frontend's job easy.

`ApiError.java`:

```java
package com.truckconnect.common;

import java.time.LocalDateTime;
import java.util.Map;

public record ApiError(int status, String message,
                       Map<String, String> fieldErrors,
                       LocalDateTime timestamp) {}
```

`GlobalExceptionHandler.java`:

```java
package com.truckconnect.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.put(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(new ApiError(
                400, "Validation failed", errors, LocalDateTime.now()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegal(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ApiError(
                400, ex.getMessage(), Map.of(), LocalDateTime.now()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(
            BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                new ApiError(401, ex.getMessage(), Map.of(),
                        LocalDateTime.now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex) {
        // Log ex properly in a real deployment; never expose stack traces
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                new ApiError(500, "Something went wrong. Please try again.",
                        Map.of(), LocalDateTime.now()));
    }
}
```

Beginner note: JWT parsing errors inside `AuthService.refresh` surface through the generic handler above in this simple version. A production version would add a specific handler that returns `401` for expired or malformed refresh tokens. Keep that as a known improvement, not a surprise.

### File 14 — `TruckConnectApplication.java` — the starting point

```java
package com.truckconnect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TruckConnectApplication {
    public static void main(String[] args) {
        SpringApplication.run(TruckConnectApplication.class, args);
    }
}
```

**In simple words:** This is the `main` method — press Run here and the whole application starts: web server, security, and database connection together.


---

## Part 7 — Password safety with BCrypt

Read this part slowly. It is the heart of the module.

### What BCrypt does

- BCrypt takes the plain password and produces a long scrambled string, for example a value starting with `$2a$10$…`. The exact stored value is always different, even for the same password, because BCrypt adds a random **salt** each time.
- The salt and the cost are stored *inside* the hash string itself, so verification needs nothing extra.
- Checking a login does **not** decrypt the hash. `passwordEncoder.matches(typedPassword, storedHash)` hashes the typed password with the stored salt and compares.
- BCrypt is deliberately a little slow (the default cost factor is 10). That slowness barely affects one real login, but it makes guessing millions of passwords painfully slow for an attacker.

### Rules that never change

1. Never save a plain password anywhere — not in the database, not in a log, not in an email.
2. Never return a password or a password hash in any API response. That is why responses use `UserResponse` and `AuthResponse`, which have no password field.
3. Never write your own hashing code. Use Spring's `BCryptPasswordEncoder`, exactly as in `SecurityConfig`.
4. Never compare passwords with `equals()`. Always use `passwordEncoder.matches(...)`, or let `AuthenticationManager` do it, as `AuthService.login` does.
5. If the database ever leaks, hashed passwords are still sensitive — but the attacker must crack each one separately, which buys users time to reset. That is the whole point of hashing.

You can verify this yourself after registering a test user: open MySQL, run `SELECT email, password_hash FROM users;`, and confirm the value is a BCrypt hash and not the password you typed.

---

## Part 8 — Validation and CORS in one place

### Validation — checking input before it causes damage

Validation lives in three layers in this module:

- **DTO annotations** (Part 6, File 6): `@NotBlank`, `@Email`, `@Pattern`, `@Size`, and `@NotNull` reject empty names, malformed emails, wrong phone formats, and short passwords before the service runs. `@Valid` on the controller parameter is what switches them on — forget `@Valid` and the annotations do nothing.
- **Service checks** in `AuthService`: duplicate email and the "no ADMIN by registration" rule cannot be expressed as simple annotations, so they are checked in code.
- **Database constraints** on `User`: `unique` on email and `nullable = false` on required columns are the last line of defence, even if some other code path tries to save bad data.

Every validation failure returns HTTP `400` with the `fieldErrors` map from `GlobalExceptionHandler`, so the frontend can show the message next to the exact field that failed.

### CORS — letting the React frontend in, and nobody else by default

Browsers block a web page on one origin (for example `http://localhost:5173`, the Vite React dev server) from calling an API on another origin (`http://localhost:8080`) unless the API explicitly allows it. That browser rule is CORS.

In this module, CORS is configured once in `SecurityConfig.corsConfigurationSource()`:

- Allowed origin: only the value of `app.cors.allowed-origin` (environment variable `CORS_ORIGIN`, default `http://localhost:5173`). In production, set it to the real frontend domain.
- Allowed methods: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`.
- Allowed headers: `Authorization` and `Content-Type` — the two the frontend actually sends.
- Credentials: `false`, because tokens travel in a header, not in cookies.

Do not "fix" a CORS error by allowing `*` (every origin) in production. That would let any website call the API from a visitor's browser. If you hit a CORS error while learning, check first that the frontend origin matches `CORS_ORIGIN` exactly, including the port.


---

## Part 9 — Run it and test it: a simple checklist

### Before you run

- [ ] MySQL 8 is running and the `truckconnect` database exists.
- [ ] `DB_USERNAME`, `DB_PASSWORD`, and a Base64 `JWT_SECRET` (at least 256 bits) are set as environment variables.
- [ ] All 14 files from Parts 5 and 6 are in the packages shown in the folder structure.

Run with `./mvnw spring-boot:run` (macOS/Linux) or `mvnw.cmd spring-boot:run` (Windows). The app starts on port 8080, and Hibernate creates the `users` table on first run.

### Test checklist — do these in order

Use Postman, or `curl` from a terminal. Replace example values with your own test values.

**1. Register a customer — expect `201 Created`**

```bash
curl -i -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Test Customer","email":"customer@test.com","phone":"9876543210","password":"Test@1234","role":"CUSTOMER"}'
```

The response shows id, name, email, phone, and role — and **no password field**.

- [ ] Register works and returns `201`
- [ ] Response contains no password and no hash

**2. Check the database**

```sql
SELECT id, email, role, password_hash FROM users;
```

- [ ] The row exists, and `password_hash` starts with `$2a$` or `$2b$` (BCrypt), not the plain password

**3. Try the mistakes on purpose**

- [ ] Registering the same email again returns `400` with "Email is already registered"
- [ ] Registering with `"role":"ADMIN"` returns `400`
- [ ] Registering with a short password (under 8 characters) returns `400` with a field error for `password`
- [ ] Registering with a bad email or a phone that is not a 10-digit Indian mobile returns `400` with a field error

**4. Login — expect `200` and two tokens**

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"customer@test.com","password":"Test@1234"}'
```

- [ ] Response contains `accessToken`, `refreshToken`, and `tokenType: "Bearer"`
- [ ] Login with a wrong password returns `401` with "Invalid email or password"
- [ ] Login with an unregistered email returns the *same* `401` message

**5. Call a protected endpoint**

Save the access token in a shell variable (`ACCESS=<paste token>`), then:

```bash
# Without a token — expect 401
curl -i http://localhost:8080/api/auth/me

# With the access token — expect 200 and your own details
curl http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer $ACCESS"

# With the REFRESH token in place of the access token — expect 401
curl -i http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer $REFRESH"
```

- [ ] No token gives `401`
- [ ] Access token gives `200` with your details
- [ ] Refresh token used as an access token gives `401`

**6. Refresh the tokens**

```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<paste refresh token>"}'
```

- [ ] Refresh returns a *new* access token and a *new* refresh token
- [ ] The new access token works on `GET /api/auth/me`
- [ ] Sending an *access* token to `/api/auth/refresh` is rejected

**7. Role rule smoke test**

There is no admin endpoint yet, but you can prove the rule exists: log in as the `CUSTOMER` and call any `/api/admin/...` URL, for example `GET /api/admin/users` with the customer's access token. Expect `403 Forbidden` (the endpoint itself will be `404`/`403` territory until a later module adds it — what matters is that a non-admin is **not** allowed through). After a real admin endpoint is added, this becomes a strict pass/fail test.

**8. Create the first ADMIN (manual, one time only)**

Register a normal account for the admin person, then promote it directly in MySQL — only someone with database access can do this, which is exactly why it is safe:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'admin@test.com';
```

- [ ] That account can now log in and its login response shows `role: "ADMIN"`

**9. CORS smoke test**

- [ ] With the React app (or any page) served from `http://localhost:5173`, a login call from the browser succeeds
- [ ] The same browser call from a different origin is blocked by the browser with a CORS error

### API summary

| Method and URL | Who can call it | Success | Main failures |
| --- | --- | --- | --- |
| `POST /api/auth/register` | Anyone | `201` | `400` validation / duplicate / ADMIN role |
| `POST /api/auth/login` | Anyone | `200` + tokens | `401` invalid email or password |
| `POST /api/auth/refresh` | Anyone with a valid refresh token | `200` + new tokens | Invalid or expired refresh token |
| `GET /api/auth/me` | Any logged-in user | `200` own details | `401` no / bad / expired token |
| `/api/admin/**` | ADMIN only | Later module | `403` for every other role |


---

## Part 10 — Common beginner mistakes

| Mistake | What happens | Fix |
| --- | --- | --- |
| Saving `request.password()` directly | Plain passwords sit in the database | Always `passwordEncoder.encode(...)` in `AuthService.register` |
| Forgetting `@Valid` on a controller parameter | Validation annotations never run | Put `@Valid @RequestBody` on every request DTO parameter |
| Role stored as a number | Database shows `0`, `1`, `2` and breaks if the enum reorders | Keep `@Enumerated(EnumType.STRING)` on `User.role` |
| Checking `hasRole('ROLE_CUSTOMER')` | Role check never matches | `hasRole` adds `ROLE_` itself — write `hasRole("CUSTOMER")` |
| Using a refresh token as an access token | Confusing `401`s, or worse, it works when it should not | Keep the `type` claim check in `JwtAuthFilter` and `AuthService.refresh` |
| Short or plain-text `JWT_SECRET` | App fails to start, or tokens can be forged | Use a Base64 secret of at least 256 bits, from the environment only |
| Committing secrets to GitHub | Anyone with the repo can log in as anyone | Secrets in environment variables only; add `.env` to `.gitignore` |
| Returning the `User` entity from a controller | Password hash leaks in the JSON | Always return a DTO (`UserResponse` / `AuthResponse`) |
| Allowing every CORS origin (`*`) in production | Any website can call the API from a browser | Set `CORS_ORIGIN` to the real frontend domain |

---

## Summary — what you now have

After Module 1, TruckConnect can:

- Register a `CUSTOMER`, `OWNER`, `DRIVER`, or `TRANSPORTER` — never an `ADMIN` through the public API
- Store passwords only as BCrypt hashes
- Log a user in and issue a 15-minute access token plus a 7-day refresh token, with rotation on refresh
- Recognise the logged-in user on every request through `JwtAuthFilter`
- Protect every `/api/**` endpoint behind login, and `/api/admin/**` behind the `ADMIN` role
- Validate every input and return clean, field-level error messages
- Accept browser calls only from the configured frontend origin
- Keep every secret in environment variables, outside the code and outside GitHub

## What comes next — only when you say so

Module 2 is **not** built in this guide. The natural next step is user profile and truck management, because trucks belong to owners and transporters whose identities Module 1 now proves. When you are ready, ask for Module 2 in the same part-by-part style, and it will reuse these users, roles, and security rules without changing them.
