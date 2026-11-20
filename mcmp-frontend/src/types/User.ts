class User {
  sub = "";

  // LHM
  displayName = "";
  surname = "";
  telephoneNumber = "";
  email = "";
  username = "";
  givenname = "";
  department = "";
  lhmObjectID = "";
  // LHM_Extended
  preferred_username = "";
  memberof: string[] = [];
  user_roles: string[] = [];
  authorities: string[] = [];
}

export class UserHistoryItem extends User {
  id: number | string = "";
  name = "";
  createdAt = ""; // Erster Login
  updatedAt = ""; // Letzte Aktivität
}

export function UserLocalDevelopment(): User {
  const u = new User();
  u.username = "Local Development User";
  u.displayName = "Local Development User";
  u.authorities = [
    // todo add authorities
  ];
  u.user_roles = [
    // todo add user roles
  ];
  return u;
}

export default User;
