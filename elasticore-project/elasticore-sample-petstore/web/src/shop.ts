// Signing up and being signed in by it - the one part of the API written by
// hand. Signing in and out and "who am I" are the generated client's, from
// @login and @password; who may call what, and whose orders a search
// returns, is decided by the server from the DSL.
import { request } from '../../src/main/ts/apiSupport';
import { CustomerDTO } from '../../src/main/ts/CustomerDTO';

export { signIn, signOut, me } from '../../src/main/ts/CustomerApi';

export const signUp = (customer: CustomerDTO) =>
    request<CustomerDTO>('POST', '/api/petstore/account/signup', customer);
