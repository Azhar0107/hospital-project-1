package kz.iitu.itis1908.hospitalmanagementservice.service.impl;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import kz.iitu.itis1908.hospitalmanagementservice.exceptions.security.CustomSecurityException;
import kz.iitu.itis1908.hospitalmanagementservice.model.Constants.ApiMessages;
import kz.iitu.itis1908.hospitalmanagementservice.model.dto.UserChangePasswordDTO;
import kz.iitu.itis1908.hospitalmanagementservice.model.dto.UserLoginDto;
import kz.iitu.itis1908.hospitalmanagementservice.model.dto.UserRegisterDto;
import kz.iitu.itis1908.hospitalmanagementservice.model.entity.Role;
import kz.iitu.itis1908.hospitalmanagementservice.model.entity.User;
import kz.iitu.itis1908.hospitalmanagementservice.repository.RoleRepository;
import kz.iitu.itis1908.hospitalmanagementservice.repository.UserRepository;
import kz.iitu.itis1908.hospitalmanagementservice.security.AccessToken;
import kz.iitu.itis1908.hospitalmanagementservice.security.ITokenProvider;
import kz.iitu.itis1908.hospitalmanagementservice.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

  private final ITokenProvider tokenProvider;

  private final PasswordEncoder passwordEncoder;

  private final UserRepository userRepository;

  private final RoleRepository roleRepository;

  private final AuthenticationManager authenticationManager;

  @Override
  public AccessToken register(UserRegisterDto userRegisterDto) {
    if (!userRegisterDto.getUsername().startsWith("+7")) {
      throw new CustomSecurityException(ApiMessages.INVALID_USERNAME, HttpStatus.BAD_REQUEST);
    }
    String username = null;
    Set<Role> roles = null;
    try {
      checkUserExistsWithUserName(userRegisterDto.getUsername());
      User user = User.builder()
          .username(userRegisterDto.getUsername())
          .password(passwordEncoder.encode(userRegisterDto.getPassword()))
          .roles(getRoles(userRegisterDto.getRoles()))
          .build();
      username = user.getUsername();
      roles = user.getRoles();
      userRepository.save(user);
      log.info("User {} successfully registered with role {}", userRegisterDto.getUsername(),
          userRegisterDto.getRoles());
    } catch (Exception e) {
      log.error(e.getMessage());
    }

    return tokenProvider.createToken(username, roles);
  }

  @Override
  public AccessToken login(UserLoginDto userLoginDto) {
    String username = userLoginDto.getUsername();
    String password = userLoginDto.getPassword();
    try {
      Authentication authentication = authenticationManager.authenticate(
          new UsernamePasswordAuthenticationToken(username, password));
      Set<Role> roles = userRepository.findByUsername(username).get().getRoles();
      log.info("User successfully login {}", userLoginDto.getUsername());
      return tokenProvider.createToken(username, roles);

    } catch (AuthenticationException exception) {
      log.error(exception.getMessage());
      throw new CustomSecurityException(ApiMessages.BAD_CREDENTIALS, HttpStatus.BAD_REQUEST);

    }

  }

  private void checkUserExistsWithUserName(String username) {
    if (userRepository.existsByUsername(username)) {
      throw new CustomSecurityException(ApiMessages.USER_ALREADY_EXISTS, HttpStatus.BAD_REQUEST);
    }
  }

  private Set<Role> getRoles(String[] roles) {
    Set<Role> userRoles = new HashSet<>();
    for (String role : roles) {
      userRoles.add(roleRepository.findByName(role));
    }
    return userRoles;
  }

  @Override
  public void changePassword(UserChangePasswordDTO userChangePasswordDTO) {
    User user = userRepository.findByUsername(userChangePasswordDTO.getUsername()).orElseThrow(
        () -> new CustomSecurityException(ApiMessages.BAD_CREDENTIALS, HttpStatus.BAD_REQUEST));
    log.info("Find user {} to change password", user.getUsername());
    if (!passwordEncoder.matches(userChangePasswordDTO.getPassword(), user.getPassword())) {
      throw new CustomSecurityException(ApiMessages.BAD_CREDENTIALS, HttpStatus.BAD_REQUEST);
    }

    user.setPassword(passwordEncoder.encode(userChangePasswordDTO.getReTypedPassword()));

    userRepository.save(user);
    log.info("User {} successfully changed password", user.getUsername());
  }
}
