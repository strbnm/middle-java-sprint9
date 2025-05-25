package ru.strbnm.accounts_service.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.strbnm.accounts_service.domain.UserRequest;
import ru.strbnm.accounts_service.entity.User;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "enabled", ignore = true)
    User mapToUserEntity(UserRequest userRequest);
}
